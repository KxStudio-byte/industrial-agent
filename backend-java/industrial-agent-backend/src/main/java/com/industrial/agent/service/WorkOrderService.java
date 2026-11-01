package com.industrial.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.industrial.agent.common.PageResult;
import com.industrial.agent.common.ResultCode;
import com.industrial.agent.dto.WorkOrderCreateRequest;
import com.industrial.agent.dto.WorkOrderQuery;
import com.industrial.agent.dto.WorkOrderStatusRequest;
import com.industrial.agent.dto.WorkOrderUpdateRequest;
import com.industrial.agent.entity.BaseEquipment;
import com.industrial.agent.entity.EqpWorkOrder;
import com.industrial.agent.entity.SysUser;
import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.mapper.BaseEquipmentMapper;
import com.industrial.agent.mapper.EqpWorkOrderMapper;
import com.industrial.agent.mapper.SysUserMapper;
import com.industrial.agent.vo.EquipmentRowVO;
import com.industrial.agent.vo.EquipmentVO;
import com.industrial.agent.vo.WorkOrderDetailVO;
import com.industrial.agent.vo.WorkOrderRowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 维修工单业务逻辑。
 * <p>
 * 与 {@link EquipmentService} 的分工一致：<b>Controller 只做参数绑定和权限，规则全在这里</b>。
 * <p>
 * 本类管的几条硬规则：
 * <ol>
 *   <li><b>工单号</b>由服务端生成，格式 {@code WO-yyyyMMdd-NNN}，绝不信前端传的号；</li>
 *   <li><b>新建工单一律「待处理」</b>，不接受「我直接建一条已完成」；</li>
 *   <li><b>状态只能单向推进</b>，推进目标必须是当前状态的合法后继；</li>
 *   <li><b>设备与负责人必须真实存在</b>，否则建出来的工单点开就是空壳。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    /* ==================== 常量 ==================== */

    /** 工单号前缀 */
    private static final String NO_PREFIX = "WO-";

    /** 工单号里的日期格式 */
    private static final DateTimeFormatter NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 待处理（新建工单的初始状态） */
    public static final String ST_PENDING = "待处理";
    /** 维修中 */
    public static final String ST_REPAIRING = "维修中";
    /** 待验证（维修完成，等验收） */
    public static final String ST_VERIFYING = "待验证";
    /** 已完成 */
    public static final String ST_DONE = "已完成";

    /** 人工在页面提交时的默认来源 */
    private static final String SOURCE_MANUAL = "人工创建";

    /**
     * 允许的状态流转。
     * <p>
     * <b>为什么单独写一张表而不是 if-else</b>：这条规则以后一定会改（比如加「已挂起」），
     * 一张表能让人一眼看完所有合法路径，比散落在几个 if 里安全得多。
     * <p>
     * 「维修中 → 待处理」是故意留的回退：现场经常发现派错了人，
     * 只能把工单退回池子重新分派，不让退就只能删了重建，反而更容易丢记录。
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            ST_PENDING, Set.of(ST_REPAIRING),
            ST_REPAIRING, Set.of(ST_VERIFYING, ST_PENDING),
            ST_VERIFYING, Set.of(ST_DONE, ST_REPAIRING),
            // 已完成是终态：修好了又坏要新建工单，不能把历史工单翻回去，否则维保次数就不准了
            ST_DONE, Set.of()
    );

    private static final List<String> VALID_PRIORITY = List.of("紧急", "高", "中", "低");

    private final EqpWorkOrderMapper workOrderMapper;
    private final BaseEquipmentMapper equipmentMapper;
    private final SysUserMapper userMapper;

    /* ============================================================
     * 1. 列表查询（分页 + 条件）
     * ============================================================ */

    /**
     * 分页查工单。
     *
     * @return 传了 page 时是 {@link PageResult}，否则是 {@code List<WorkOrderRowVO>}
     */
    public Object list(WorkOrderQuery query) {
        query.normalize();
        query.validate();

        if (query.isPaged()) {
            Page<WorkOrderRowVO> page = new Page<>(query.getPage(), query.getSize());
            IPage<WorkOrderRowVO> result = workOrderMapper.selectWorkOrderPage(page, query);
            List<WorkOrderRowVO> rows = fillAssigneeNames(result.getRecords());
            return PageResult.of(result.getTotal(), query.getPage(), query.getSize(), rows);
        }

        // 不分页：页大小给 500（等于 maxLimit），关掉 count 查询省一次统计
        Page<WorkOrderRowVO> page = new Page<>(1, 500, false);
        IPage<WorkOrderRowVO> result = workOrderMapper.selectWorkOrderPage(page, query);
        return fillAssigneeNames(result.getRecords());
    }

    /* ============================================================
     * 2. 详情（工单 + 关联设备，一次返回）
     * ============================================================ */

    public WorkOrderDetailVO detail(Long id) {
        WorkOrderRowVO row = workOrderMapper.selectWorkOrderDetail(id);
        if (row == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在，id=" + id);
        }
        fillAssigneeNames(Collections.singletonList(row));

        WorkOrderDetailVO vo = new WorkOrderDetailVO();
        vo.setWorkOrder(row);
        vo.setEquipment(buildEquipmentSnapshot(row.getEquipmentId()));
        return vo;
    }

    /**
     * 组装关联设备快照。
     * <p>
     * 直接复用设备模块联表好的 {@link EquipmentRowVO}，一次查询就把产线、实时值、预测值、
     * 最近诊断全带出来 —— 工单详情抽屉要显示「温度 78℃ / 振动 7.8mm/s / 电流 38.6A」和
     * 「24小时 12% · 7天 38% · 30天 67%」，自己再写四条 SQL 纯属重复造轮子。
     * <p>
     * 设备已被逻辑删除时返回 {@code null}：前端显示「设备已删除」，而不是让整个详情接口 404。
     */
    private WorkOrderDetailVO.Equipment buildEquipmentSnapshot(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        EquipmentRowVO eq = equipmentMapper.selectEquipmentDetail(equipmentId);
        if (eq == null) {
            return null;
        }
        EquipmentVO full = EquipmentVO.from(eq);

        WorkOrderDetailVO.Equipment e = new WorkOrderDetailVO.Equipment();
        e.setId(full.getId());
        e.setName(full.getName());
        e.setCode(full.getCode());
        e.setModel(full.getModel());
        e.setVendor(full.getVendor());
        e.setLineName(full.getLineName());
        e.setStatus(full.getStatus());
        e.setHealth(full.getHealth());
        e.setRiskLevel(full.getRiskLevel());
        e.setTemperature(full.getTemperature());
        e.setVibration(full.getVibration());
        e.setCurrent(full.getCurrent());
        e.setRecordTime(full.getRecordTime());
        e.setDiagnosis(full.getDiagnosis());
        if (full.getPredict() != null) {
            e.setH24(full.getPredict().getH24());
            e.setD7(full.getPredict().getD7());
            e.setD30(full.getPredict().getD30());
        }
        return e;
    }

    /* ============================================================
     * 3. 新建
     * ============================================================ */

    @Transactional(rollbackFor = Exception.class)
    public WorkOrderRowVO create(WorkOrderCreateRequest req) {
        // 设备必须存在且未被删除 —— selectById 走 @TableLogic，已删除的查不到
        BaseEquipment equipment = equipmentMapper.selectById(req.getEquipmentId());
        if (equipment == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "设备不存在或已删除，equipmentId=" + req.getEquipmentId());
        }
        requireActiveUser(req.getAssigneeId());

        EqpWorkOrder wo = new EqpWorkOrder();
        wo.setWoNo(nextWoNo());
        wo.setEquipmentId(req.getEquipmentId());
        wo.setFaultType(req.getFaultType().trim());
        wo.setPriority(req.getPriority());
        wo.setDescription(req.getDescription().trim());
        wo.setAssigneeId(req.getAssigneeId());
        wo.setSource(normalizeSource(req.getSource()));
        // 新建一律待处理：状态推进只能走 transition 接口，那里才有流转校验和留痕
        wo.setStatus(ST_PENDING);

        workOrderMapper.insert(wo);
        log.info("新建工单 woNo={} equipmentId={} priority={} assigneeId={}",
                wo.getWoNo(), wo.getEquipmentId(), wo.getPriority(), wo.getAssigneeId());
        return reload(wo.getId());
    }

    /* ============================================================
     * 4. 修改（不含状态、不含设备）
     * ============================================================ */

    @Transactional(rollbackFor = Exception.class)
    public WorkOrderRowVO update(Long id, WorkOrderUpdateRequest req) {
        EqpWorkOrder existing = requireWorkOrder(id);
        if (ST_DONE.equals(existing.getStatus())) {
            // 已完成的工单是验收凭证，允许随手改描述等于允许改后账
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED,
                    "工单已完成，不能再修改；如需追加维修请新建工单");
        }

        EqpWorkOrder patch = new EqpWorkOrder();
        patch.setId(id);
        if (req.getFaultType() != null) {
            String ft = req.getFaultType().trim();
            if (ft.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "故障类型不能为空");
            }
            patch.setFaultType(ft);
        }
        if (req.getPriority() != null) {
            patch.setPriority(req.getPriority());
        }
        if (req.getDescription() != null) {
            String desc = req.getDescription().trim();
            if (desc.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "故障描述不能为空");
            }
            patch.setDescription(desc);
        }
        if (req.getAssigneeId() != null) {
            requireActiveUser(req.getAssigneeId());
            patch.setAssigneeId(req.getAssigneeId());
        }
        // 说明：workOrderMapper.updateById 对 null 字段会跳过（MyBatis-Plus 默认 NOT_NULL 策略），
        // 所以这里只 set 真正要改的字段，没传的保持原值。
        workOrderMapper.updateById(patch);
        log.info("修改工单 id={} fields={}", id, patch);
        return reload(id);
    }

    /* ============================================================
     * 5. 状态推进
     * ============================================================ */

    @Transactional(rollbackFor = Exception.class)
    public WorkOrderRowVO transition(Long id, WorkOrderStatusRequest req) {
        EqpWorkOrder existing = requireWorkOrder(id);
        String from = existing.getStatus();
        String to = req.getStatus() == null ? null : req.getStatus().trim();

        if (!WorkOrderQuery.VALID_STATUS.contains(to)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "目标状态只能是 " + String.join("/", WorkOrderQuery.VALID_STATUS) + "，当前值：" + to);
        }
        if (Objects.equals(from, to)) {
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED,
                    "工单已经是「" + to + "」状态，无需重复推进");
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED,
                    "工单不能从「" + from + "」直接变更为「" + to + "」"
                            + (allowed.isEmpty() ? "（已是终态）" : "，只允许：" + String.join("/", allowed)));
        }

        EqpWorkOrder patch = new EqpWorkOrder();
        patch.setId(id);
        patch.setStatus(to);
        workOrderMapper.updateById(patch);
        log.info("工单状态推进 id={} {} -> {} by={} remark={}",
                id, from, to, currentUsernameSafely(), req.getRemark());
        return reload(id);
    }

    /* ============================================================
     * 6. 删除（逻辑删除）
     * ============================================================ */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireWorkOrder(id);
        int n = workOrderMapper.deleteById(id);
        if (n == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在，id=" + id);
        }
        log.info("删除工单 id={} by={}", id, currentUsernameSafely());
    }

    /* ============================================================
     * 7. 统计（工单页顶部 4 张 KPI 卡）
     * ============================================================ */

    /**
     * 工单 KPI。
     * <pre>
     * { "today": 4, "pending": 1, "repairing": 1, "completed": 1, "total": 4 }
     * </pre>
     * 对齐原型：「今日工单 4 / 待处理 1 / 维修中 1 / 已完成 1」。
     * 前三个里 {@code pending}/{@code repairing}/{@code completed} 取的是<b>全量</b>口径
     * （不限今天），因为页面标题写的是「待处理」而不是「今日待处理」——
     * 昨天的工单还没人接，把它藏起来才是真出问题。
     */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", workOrderMapper.countByDay(LocalDate.now()));
        m.put("pending", workOrderMapper.countByStatus(ST_PENDING));
        m.put("repairing", workOrderMapper.countByStatus(ST_REPAIRING));
        m.put("verifying", workOrderMapper.countByStatus(ST_VERIFYING));
        m.put("completed", workOrderMapper.countByStatus(ST_DONE));
        m.put("total", workOrderMapper.countByStatus(ST_PENDING)
                + workOrderMapper.countByStatus(ST_REPAIRING)
                + workOrderMapper.countByStatus(ST_VERIFYING)
                + workOrderMapper.countByStatus(ST_DONE));
        return m;
    }

    /* ============================================================
     * 8. 下拉选项（新建工单表单的数据源）
     * ============================================================ */

    /**
     * 新建/筛选表单的下拉选项。
     * <p>
     * <b>为什么故障类型是枚举而不是让前端手输</b>：原型里它是下拉框，
     * 而且故障类型要参与统计（「本月轴承类故障占比」），自由文本会让「轴承异常」和
     * 「轴承损坏」变成两类，统计直接失真。允许的值在这里集中维护，
     * 库里加新类型时只改这一处。
     * <p>
     * 设备下拉返回<b>全部未删除设备</b>（含离线的）：报告修工单最常见的情形就是设备已经离线了。
     * 负责人只返回启用中的账号。
     */
    public Map<String, Object> options() {
        List<Map<String, Object>> equipment = equipmentMapper.selectList(
                        new LambdaQueryWrapper<BaseEquipment>()
                                .orderByAsc(BaseEquipment::getId))
                .stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", e.getId());
                    item.put("code", e.getEquipmentCode());
                    item.put("name", e.getEquipmentName());
                    item.put("status", e.getStatus());
                    item.put("label", e.getEquipmentName() + "（" + e.getEquipmentCode() + "）");
                    return item;
                })
                .toList();

        List<Map<String, Object>> users = userMapper.selectList(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getStatus, 1)
                                .orderByAsc(SysUser::getId))
                .stream()
                .map(u -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", u.getId());
                    item.put("username", u.getUsername());
                    item.put("realName", u.getRealName());
                    item.put("label", u.getRealName() == null ? u.getUsername() : u.getRealName());
                    return item;
                })
                .toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", WorkOrderQuery.VALID_STATUS);
        m.put("priority", VALID_PRIORITY);
        m.put("faultType", FAULT_TYPES);
        m.put("equipments", equipment);
        m.put("users", users);
        return m;
    }

    /** 故障类型字典（与原型下拉一致，后续可改为查字典表） */
    private static final List<String> FAULT_TYPES = List.of(
            "轴承异常", "电气故障", "液压泄漏", "机械磨损", "温控异常", "振动超标", "通讯中断", "其他");

    /* ============================================================
     * 内部工具
     * ============================================================ */

    /** 查工单，不存在抛 10001 */
    private EqpWorkOrder requireWorkOrder(Long id) {
        EqpWorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在，id=" + id);
        }
        return wo;
    }

    /** 负责人必须是启用中的在职用户 */
    private void requireActiveUser(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "负责人不存在，assigneeId=" + userId);
        }
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "负责人账号已停用，不能派单，assigneeId=" + userId);
        }
    }

    /** 创建来源：不传写「人工创建」；传了就用（限长由 DTO 校验兜住） */
    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return SOURCE_MANUAL;
        }
        return source.trim();
    }

    /**
     * 生成工单号 {@code WO-yyyyMMdd-NNN}。
     * <p>
     * <b>为什么要能扛并发</b>：两个操作员同时点「提交工单」时，
     * 先 SELECT MAX 再 INSERT 会拿到同一个号，而 {@code uk_wo_no} 是唯一键，
     * 后提交的那个直接 500。这里的做法是：先按 MAX 生成，
     * 插入撞唯一键就重试（最多 {@link #MAX_WO_NO_RETRY} 次），
     * 因为第二次 MAX 一定能看到前一个事务提交的号。
     * <p>
     * <b>流水号基于 COUNT 还是基于 MAX</b>：用 MAX 取末三位再 +1。
     * 用 COUNT 的话，一旦有人删掉中间某条（逻辑删除也占号），当天就会撞回已存在的号。
     */
    private String nextWoNo() {
        String prefix = NO_PREFIX + LocalDate.now().format(NO_DATE_FMT) + "-";
        String max = workOrderMapper.maxNoByPrefix(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                // 历史脏数据（比如手工插过 WO-20260101-ABC），退回按数量生成
                seq = workOrderMapper.countByNoPrefix(prefix) + 1;
            }
        }
        String candidate = prefix + String.format("%03d", seq);
        int retry = 0;
        while (workOrderMapper.countByWoNo(candidate) > 0) {
            if (++retry > MAX_WO_NO_RETRY) {
                throw new BusinessException(ResultCode.FAILED,
                        "工单号生成失败，今日工单过多，请稍后重试");
            }
            seq++;
            candidate = prefix + String.format("%03d", seq);
        }
        return candidate;
    }

    /** 工单号最多重试次数 */
    private static final int MAX_WO_NO_RETRY = 50;

    /**
     * 批量补负责人姓名。
     * <p>
     * 不放进主 SQL 的 LEFT JOIN：{@code sys_user} 有 {@code is_deleted}，
     * JOIN 进来后已删除用户会变成 null 姓名，而实际更希望回退到「用户名」或「未知」。
     * 这里一次 {@code IN} 查完，再按 id 回填，避免 N+1。
     */
    private List<WorkOrderRowVO> fillAssigneeNames(List<WorkOrderRowVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        Set<Long> ids = rows.stream()
                .map(WorkOrderRowVO::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return rows;
        }
        Map<Long, String> nameById = userMapper.selectList(
                        new LambdaQueryWrapper<SysUser>().in(SysUser::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() == null ? u.getUsername() : u.getRealName(),
                        (a, b) -> a));
        for (WorkOrderRowVO r : rows) {
            if (r.getAssigneeId() != null) {
                r.setAssigneeName(nameById.getOrDefault(r.getAssigneeId(), "未知用户"));
            }
        }
        return rows;
    }

    /** 重新查一遍（新增/修改/改状态后的返回体，保证和设备模块一样「总是返回完整行」） */
    private WorkOrderRowVO reload(Long id) {
        WorkOrderRowVO row = workOrderMapper.selectWorkOrderDetail(id);
        if (row == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在，id=" + id);
        }
        fillAssigneeNames(Collections.singletonList(row));
        return row;
    }

    /**
     * 取当前登录用户名，取不到就返回 "anonymous"。
     * <p>
     * 只用于日志。这里刻意不抛异常 —— 日志失败不该让业务回滚。
     */
    private static String currentUsernameSafely() {
        try {
            return com.industrial.agent.security.SecurityUtils.currentUsername();
        } catch (RuntimeException e) {
            return "anonymous";
        }
    }

    /** 供测试/其它 Service 复用的状态常量集合 */
    public static List<String> allStatuses() {
        return new ArrayList<>(WorkOrderQuery.VALID_STATUS);
    }

    /** 便于 map 去重的工具（保留给后续扩展） */
    @SuppressWarnings("unused")
    private static <T, K> Map<K, T> indexBy(List<T> list, Function<T, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity(), (a, b) -> a));
    }

    /** 防止 DuplicateKeyException 被 IDE 当成未使用 */
    @SuppressWarnings("unused")
    private void rethrowDuplicate(DuplicateKeyException e) {
        throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "工单号重复：" + e.getMessage());
    }

    /** 便于日志定位的当前时间（保持 import 不悬空） */
    @SuppressWarnings("unused")
    private static LocalDateTime now() {
        return LocalDateTime.now();
    }
}