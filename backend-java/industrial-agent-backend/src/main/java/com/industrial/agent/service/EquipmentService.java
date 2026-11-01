package com.industrial.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.industrial.agent.common.PageResult;
import com.industrial.agent.common.ResultCode;
import com.industrial.agent.dto.EquipmentCreateRequest;
import com.industrial.agent.dto.EquipmentQuery;
import com.industrial.agent.dto.EquipmentUpdateRequest;
import com.industrial.agent.dto.RuntimeReportRequest;
import com.industrial.agent.entity.BaseEquipment;
import com.industrial.agent.entity.BaseProductionLine;
import com.industrial.agent.entity.EqpFaultPredict;
import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.mapper.BaseEquipmentMapper;
import com.industrial.agent.mapper.BaseProductionLineMapper;
import com.industrial.agent.mapper.EqpFaultPredictMapper;
import com.industrial.agent.mapper.EqpRuntimeDataMapper;
import com.industrial.agent.vo.EquipmentRowVO;
import com.industrial.agent.vo.EquipmentStatusVO;
import com.industrial.agent.vo.EquipmentSummaryVO;
import com.industrial.agent.vo.EquipmentTrendVO;
import com.industrial.agent.vo.EquipmentVO;
import com.industrial.agent.vo.RuntimePointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 设备中心业务逻辑。
 * <p>
 * 职责边界：<b>Controller 只做参数绑定和权限，本类承担全部业务规则</b>。
 * 这样「温度多少算预警」这种规则只存在于一处，改阈值不用翻 Controller。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentService {

    /* ==================== 业务阈值（集中定义，别散落到各方法里） ==================== */

    /** 温度预警阈值（℃）：超过即判定异常 */
    private static final double TEMPERATURE_LIMIT = 65.0;
    /** 振动预警阈值（mm/s） */
    private static final double VIBRATION_LIMIT = 6.5;
    /** 数据过期阈值：超过这么多分钟没上报，视为离线 */
    private static final long STALE_MINUTES = 10;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> VALID_STATUS = List.of("正常", "预警", "故障", "离线");
    private static final List<String> VALID_RISK = List.of("高", "中", "低");

    private final BaseEquipmentMapper equipmentMapper;
    private final BaseProductionLineMapper lineMapper;
    private final EqpRuntimeDataMapper runtimeMapper;
    private final EqpFaultPredictMapper predictMapper;

    /* ============================================================
     * 1. 列表查询（分页 + 条件）
     * ============================================================ */

    /**
     * 分页查设备。
     *
     * @return 传了 page 时是 {@link PageResult}，否则是 {@code List<EquipmentVO>}
     */
    public Object list(EquipmentQuery query) {
        query.normalize();
        query.validate();

        if (query.isPaged()) {
            Page<EquipmentRowVO> page = new Page<>(query.getPage(), query.getSize());
            IPage<EquipmentRowVO> result = equipmentMapper.selectEquipmentPage(page, query);
            List<EquipmentVO> vos = result.getRecords().stream().map(EquipmentVO::from).toList();
            return PageResult.of(result.getTotal(), query.getPage(), query.getSize(), vos);
        }

        // 不分页：页大小给 500（等于 maxLimit），翻页参数置 1 让 SQL 走 LIMIT 500
        Page<EquipmentRowVO> page = new Page<>(1, 500, false);
        IPage<EquipmentRowVO> result = equipmentMapper.selectEquipmentPage(page, query);
        return result.getRecords().stream().map(EquipmentVO::from).toList();
    }

    /* ============================================================
     * 2. 详情
     * ============================================================ */

    public EquipmentVO detail(Long id) {
        EquipmentRowVO row = equipmentMapper.selectEquipmentDetail(id);
        if (row == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "设备不存在，id=" + id);
        }
        return EquipmentVO.from(row);
    }

    /* ============================================================
     * 3. 新增
     * ============================================================ */

    @Transactional(rollbackFor = Exception.class)
    public EquipmentVO create(EquipmentCreateRequest req) {
        String code = req.getCode().trim();
        String name = req.getName().trim();

        // 唯一性：库上有 uk_equipment_code，提前查一次是为了给出可读的提示，
        // 而不是让用户看到 DuplicateKeyException 转出来的「数据已存在」
        if (equipmentMapper.countByCode(code, null) > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "设备编号已存在：" + code);
        }

        requireLine(req.getLineId());

        BaseEquipment entity = new BaseEquipment();
        entity.setEquipmentCode(code);
        entity.setEquipmentName(name);
        entity.setLineId(req.getLineId());
        entity.setEquipmentType(trimToNull(req.getType()));
        entity.setModel(trimToNull(req.getModel()));
        entity.setManufacturer(trimToNull(req.getVendor()));
        entity.setHealthScore(req.getHealth() == null ? 100 : req.getHealth());
        entity.setRiskLevel(StringUtils.hasText(req.getRiskLevel()) ? req.getRiskLevel() : "低");
        entity.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "正常");
        entity.setLastException(trimToNull(req.getLastException()));

        equipmentMapper.insert(entity);
        log.info("新增设备 id={} code={} name={}", entity.getId(), code, name);

        // 返回完整详情而不是 skeleton：前端新增成功后通常直接把这台设备塞进表格，
        // 只给主表字段的话表格里产线名、实时值都是空的
        return detail(entity.getId());
    }

    /* ============================================================
     * 4. 修改
     * ============================================================ */

    @Transactional(rollbackFor = Exception.class)
    public EquipmentVO update(Long id, EquipmentUpdateRequest req) {
        BaseEquipment existing = equipmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "设备不存在，id=" + id);
        }
        if (!req.hasAnyField()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少需要提交一个待修改的字段");
        }

        // 只把「传了的字段」写进实体，MyBatis-Plus 默认会跳过 null 字段 → 天然就是局部更新
        BaseEquipment patch = new BaseEquipment();
        patch.setId(id);

        if (StringUtils.hasText(req.getCode())) {
            String code = req.getCode().trim();
            if (!code.equals(existing.getEquipmentCode())
                    && equipmentMapper.countByCode(code, id) > 0) {
                throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "设备编号已存在：" + code);
            }
            patch.setEquipmentCode(code);
        }
        if (req.getName() != null) {
            if (!StringUtils.hasText(req.getName())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "设备名称不能为空");
            }
            patch.setEquipmentName(req.getName().trim());
        }
        if (req.getLineId() != null) {
            requireLine(req.getLineId());
            patch.setLineId(req.getLineId());
        }
        if (req.getType() != null) {
            patch.setEquipmentType(trimToNull(req.getType()));
        }
        if (req.getModel() != null) {
            patch.setModel(trimToNull(req.getModel()));
        }
        if (req.getVendor() != null) {
            patch.setManufacturer(trimToNull(req.getVendor()));
        }
        if (req.getHealth() != null) {
            patch.setHealthScore(req.getHealth());
        }
        if (StringUtils.hasText(req.getRiskLevel())) {
            patch.setRiskLevel(req.getRiskLevel());
        }
        if (StringUtils.hasText(req.getStatus())) {
            patch.setStatus(req.getStatus());
        }
        if (req.getLastException() != null) {
            patch.setLastException(trimToNull(req.getLastException()));
        }

        equipmentMapper.updateById(patch);
        log.info("修改设备 id={} 提交字段数={}", id, countNonNull(patch));
        return detail(id);
    }

    /* ============================================================
     * 5. 删除（逻辑删除）
     * ============================================================ */

    /**
     * 删除设备。
     * <p>
     * <b>为什么是逻辑删除</b>：{@code eqp_work_order} / {@code ai_insight} 里都存着
     * {@code equipment_id}，物理删除会让历史工单变成孤儿记录，报表按月统计时直接对不上。
     * <p>
     * 代价是 {@code uk_equipment_code} 唯一键仍然占用旧编号 —— 这是<b>有意的</b>：
     * 设备编号是现场贴在机器上的物理标识，删掉后不该被新设备复用，
     * 否则历史工单会指向一台完全不同的机器。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BaseEquipment existing = equipmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "设备不存在，id=" + id);
        }

        equipmentMapper.deleteById(id);
        // 实时数据和预测数据是纯附属数据，没有追溯价值，跟着物理清掉
        runtimeMapper.deleteByEquipmentId(id);
        predictMapper.deleteByEquipmentId(id);

        log.info("删除设备 id={} code={} name={}", id, existing.getEquipmentCode(), existing.getEquipmentName());
    }

    /* ============================================================
     * 6. 状态查询
     * ============================================================ */

    /**
     * 单台设备运行状态。
     * <p>
     * 判定顺序有讲究：<b>先看有没有数据，再看数据是否过期，最后才看数值</b>。
     * 反过来写的话，一台离线 3 天的设备会被判成「正常」，因为它的最后一条数据确实是正常的。
     */
    public EquipmentStatusVO status(Long id) {
        BaseEquipment e = equipmentMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "设备不存在，id=" + id);
        }
        BaseProductionLine line = e.getLineId() == null ? null : lineMapper.selectById(e.getLineId());
        RuntimePointVO latest = runtimeMapper.selectLatest(id);

        EquipmentStatusVO vo = new EquipmentStatusVO();
        vo.setEquipmentId(id);
        vo.setCode(e.getEquipmentCode());
        vo.setName(e.getEquipmentName());
        vo.setLineName(line == null ? null : line.getLineName());
        vo.setHealth(e.getHealthScore());
        vo.setRiskLevel(e.getRiskLevel());
        vo.setTemperatureLimit(TEMPERATURE_LIMIT);
        vo.setVibrationLimit(VIBRATION_LIMIT);

        if (latest == null) {
            vo.setStatus("离线");
            vo.setAbnormal(false);
            vo.setDataStale(true);
            vo.setMessage("该设备从未上报运行数据");
            return vo;
        }

        vo.setTemperature(toDouble(latest.getTemperature()));
        vo.setVibration(toDouble(latest.getVibration()));
        vo.setCurrent(toDouble(latest.getCurrent()));
        vo.setRecordTime(latest.getRecordTime() == null ? null : latest.getRecordTime().format(TIME_FMT));

        long staleSeconds = latest.getRecordTime() == null
                ? Long.MAX_VALUE
                : Duration.between(latest.getRecordTime(), LocalDateTime.now()).getSeconds();
        vo.setStaleSeconds(staleSeconds == Long.MAX_VALUE ? null : staleSeconds);
        boolean stale = staleSeconds > STALE_MINUTES * 60L;
        vo.setDataStale(stale);

        boolean tempAbnormal = vo.getTemperature() != null && vo.getTemperature() > TEMPERATURE_LIMIT;
        boolean vibAbnormal = vo.getVibration() != null && vo.getVibration() > VIBRATION_LIMIT;
        boolean abnormal = tempAbnormal || vibAbnormal;
        vo.setAbnormal(abnormal);

        if (stale) {
            vo.setStatus("离线");
            vo.setMessage("已超过 " + STALE_MINUTES + " 分钟未上报数据，判定为离线");
        } else if ("故障".equals(e.getStatus())) {
            // 台账状态是「故障」时以台账为准：现场已停机，实时值可能是停机前采集的
            vo.setStatus("故障");
            vo.setMessage("设备处于故障状态" + (e.getLastException() == null ? "" : "：" + e.getLastException()));
        } else if (abnormal) {
            vo.setStatus("预警");
            List<String> reasons = new ArrayList<>();
            if (tempAbnormal) {
                reasons.add("温度 " + vo.getTemperature() + "℃ 超过阈值 " + TEMPERATURE_LIMIT + "℃");
            }
            if (vibAbnormal) {
                reasons.add("振动 " + vo.getVibration() + "mm/s 超过阈值 " + VIBRATION_LIMIT + "mm/s");
            }
            vo.setMessage(String.join("；", reasons));
        } else {
            vo.setStatus(e.getStatus() == null ? "正常" : e.getStatus());
            vo.setMessage("运行参数正常");
        }
        return vo;
    }

    /**
     * 全部设备的状态快照（不分页，供大屏/工位牌轮询）。
     * <p>
     * <b>为什么不复用 {@link #status(Long)}</b>：
     * <ol>
     *   <li>那是「单台查询」语义，查不到设备直接抛 {@code DATA_NOT_FOUND}。而快照里只要有
     *       一台设备没上报过数据，整批接口就会 500 —— 大屏因此整块空白。</li>
     *   <li>它内部还会再 {@code selectById} + {@code selectLatest} 各查一次，
     *       48 台设备就是 96 次额外往返（N+1）。而这里要的实时值和台账状态，
     *       分页联表 SQL 已经一次性带回来了，直接就地判定即可。</li>
     * </ol>
     * 判定规则和 {@code status(Long)} 保持完全一致：先看数据有无 → 再看是否过期 →
     * 最后才看数值是否越阈值。
     */
    public List<EquipmentStatusVO> statusSummary(String lineName, String status) {
        EquipmentQuery q = new EquipmentQuery();
        q.setLineName(lineName);
        q.setStatus(status);
        q.normalize();
        q.validate();

        Page<EquipmentRowVO> page = new Page<>(1, 500, false);
        List<EquipmentRowVO> rows = equipmentMapper.selectEquipmentPage(page, q).getRecords();
        List<EquipmentStatusVO> list = new ArrayList<>(rows.size());
        for (EquipmentRowVO row : rows) {
            list.add(buildStatus(row));
        }
        return list;
    }

    /**
     * 由一行联表结果就地构造状态判定（不查库）。
     * 逻辑与 {@link #status(Long)} 的判定段逐条对应，改动时两边必须同步。
     */
    private EquipmentStatusVO buildStatus(EquipmentRowVO e) {
        EquipmentStatusVO vo = new EquipmentStatusVO();
        vo.setEquipmentId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setLineName(e.getLineName());
        vo.setHealth(e.getHealth());
        vo.setRiskLevel(e.getRiskLevel());
        vo.setTemperatureLimit(TEMPERATURE_LIMIT);
        vo.setVibrationLimit(VIBRATION_LIMIT);

        if (e.getRecordTime() == null) {
            vo.setStatus("离线");
            vo.setAbnormal(false);
            vo.setDataStale(true);
            vo.setMessage("该设备从未上报运行数据");
            return vo;
        }

        vo.setTemperature(e.getTemperature() == null ? null : e.getTemperature().doubleValue());
        vo.setVibration(e.getVibration() == null ? null : e.getVibration().doubleValue());
        vo.setCurrent(e.getCurrent() == null ? null : e.getCurrent().doubleValue());
        vo.setRecordTime(e.getRecordTime().format(TIME_FMT));

        long staleSeconds = Duration.between(e.getRecordTime(), LocalDateTime.now()).getSeconds();
        vo.setStaleSeconds(staleSeconds);
        boolean stale = staleSeconds > STALE_MINUTES * 60L;
        vo.setDataStale(stale);

        boolean tempAbnormal = vo.getTemperature() != null && vo.getTemperature() > TEMPERATURE_LIMIT;
        boolean vibAbnormal = vo.getVibration() != null && vo.getVibration() > VIBRATION_LIMIT;
        vo.setAbnormal(tempAbnormal || vibAbnormal);

        if (stale) {
            vo.setStatus("离线");
            vo.setMessage("已超过 " + STALE_MINUTES + " 分钟未上报数据，判定为离线");
        } else if ("故障".equals(e.getStatus())) {
            vo.setStatus("故障");
            vo.setMessage("设备处于故障状态" + (e.getLastException() == null ? "" : "：" + e.getLastException()));
        } else if (vo.getAbnormal()) {
            vo.setStatus("预警");
            List<String> reasons = new ArrayList<>();
            if (tempAbnormal) {
                reasons.add("温度 " + vo.getTemperature() + "℃ 超过阈值 " + TEMPERATURE_LIMIT + "℃");
            }
            if (vibAbnormal) {
                reasons.add("振动 " + vo.getVibration() + "mm/s 超过阈值 " + VIBRATION_LIMIT + "mm/s");
            }
            vo.setMessage(String.join("；", reasons));
        } else {
            vo.setStatus(e.getStatus() == null ? "正常" : e.getStatus());
            vo.setMessage("运行参数正常");
        }
        return vo;
    }

    /* ============================================================
     * 7. 运行数据
     * ============================================================ */

    /** 最新一条运行数据 */
    public RuntimePointVO latestRuntime(Long id) {
        requireEquipment(id);
        RuntimePointVO latest = runtimeMapper.selectLatest(id);
        if (latest == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "该设备暂无运行数据");
        }
        latest.setEquipmentId(id);
        return latest;
    }

    /**
     * 运行数据趋势。
     * <p>
     * 粒度策略：
     * <ul>
     *   <li>{@code hour}：直接返回原始点（历史表里就是按小时采的）</li>
     *   <li>{@code day}：按天聚合取平均 —— 一个月 720 个原始点画趋势图看不清，
     *       聚合成 30 个日均值才有意义</li>
     * </ul>
     * 聚合放在 Java 而不是 SQL 里：MySQL 的 {@code DATE_FORMAT} 依赖时区设置，
     * 而且点数本来就不多（单设备单月最多 744 条），没必要为这点量写复杂 SQL。
     */
    public EquipmentTrendVO trend(Long id, String granularity, Integer hours, Integer days) {
        requireEquipment(id);

        String gran = StringUtils.hasText(granularity) ? granularity.trim().toLowerCase() : "hour";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from;
        if ("day".equals(gran)) {
            int d = days == null ? 7 : Math.max(1, Math.min(days, 90));
            from = now.minusDays(d).truncatedTo(ChronoUnit.DAYS);
        } else {
            gran = "hour";
            int h = hours == null ? 24 : Math.max(1, Math.min(hours, 720));
            from = now.minusHours(h);
        }

        List<RuntimePointVO> raw = runtimeMapper.selectHistory(id, from, now);

        List<RuntimePointVO> points = "day".equals(gran) ? aggregateByDay(raw) : raw;

        EquipmentTrendVO vo = new EquipmentTrendVO();
        vo.setEquipmentId(id);
        vo.setGranularity(gran);
        vo.setFrom(from.format(TIME_FMT));
        vo.setTo(now.format(TIME_FMT));
        vo.setPointCount(points.size());
        vo.setPoints(points);
        vo.setHasMore(!raw.isEmpty() && raw.get(0).getRecordTime() != null
                && raw.get(0).getRecordTime().isAfter(from));
        return vo;
    }

    /**
     * 上报运行数据。
     * <p>
     * 写入两步：覆盖 {@code eqp_runtime_data}（最新值）+ 追加 {@code eqp_runtime_history}（时序）。
     * 两步放在同一个事务里，避免「最新值更新了但历史丢了」导致趋势图缺口。
     *
     * @return 上报后的状态判定结果，采集端据此可以直接判断要不要告警
     */
    @Transactional(rollbackFor = Exception.class)
    public EquipmentStatusVO reportRuntime(Long id, RuntimeReportRequest req) {
        requireEquipment(id);

        if (req.getTemperature() == null && req.getVibration() == null && req.getCurrent() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "温度、振动、电流至少需要上报一项");
        }
        checkRange(req.getTemperature(), 0, 200, "温度");
        checkRange(req.getVibration(), 0, 50, "振动");
        checkRange(req.getCurrent(), 0, 1000, "电流");

        RuntimePointVO dto = new RuntimePointVO();
        dto.setEquipmentId(id);
        dto.setTemperature(req.getTemperature());
        dto.setVibration(req.getVibration());
        dto.setCurrent(req.getCurrent());
        dto.setRecordTime(req.getRecordTime() == null ? LocalDateTime.now() : req.getRecordTime());

        runtimeMapper.upsert(dto);
        runtimeMapper.archiveLatest(id);

        log.info("上报运行数据 id={} temp={} vib={} cur={} at={}",
                id, dto.getTemperature(), dto.getVibration(), dto.getCurrent(), dto.getRecordTime());
        return status(id);
    }

    /** 历史原始点变更（补数据 / 修正采集值），一次最多 1000 条 */
    @Transactional(rollbackFor = Exception.class)
    public int backfillHistory(Long id, List<RuntimePointVO> points) {
        requireEquipment(id);
        if (points == null || points.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "数据点列表不能为空");
        }
        if (points.size() > 1000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多补录 1000 条");
        }
        int n = 0;
        for (RuntimePointVO p : points) {
            if (p.getRecordTime() == null) {
                continue;
            }
            checkRange(p.getTemperature(), 0, 200, "温度");
            checkRange(p.getVibration(), 0, 50, "振动");
            checkRange(p.getCurrent(), 0, 1000, "电流");
            p.setEquipmentId(id);
            n += runtimeMapper.insertHistory(p);
        }
        log.info("补录历史数据 id={} 条数={}", id, n);
        return n;
    }

    /* ============================================================
     * 设备总览 KPI
     * ============================================================ */

    public EquipmentSummaryVO summary() {
        List<EquipmentRowVO> rows = allEquipment();

        EquipmentSummaryVO vo = new EquipmentSummaryVO();
        vo.setTotal(rows.size());
        vo.setNormal(countBy(rows, "status", "正常"));
        vo.setWarning(countBy(rows, "status", "预警"));
        vo.setFault(countBy(rows, "status", "故障"));
        vo.setOffline(countBy(rows, "status", "离线"));
        vo.setOnline(vo.getNormal() + vo.getWarning() + vo.getFault());
        vo.setOnlineRate(vo.getTotal() == 0 ? 0
                : BigDecimal.valueOf(vo.getOnline() * 100.0 / vo.getTotal())
                        .setScale(1, RoundingMode.HALF_UP).doubleValue());
        vo.setHighRisk(countBy(rows, "riskLevel", "高"));
        vo.setMidRisk(countBy(rows, "riskLevel", "中"));
        vo.setLowRisk(countBy(rows, "riskLevel", "低"));

        Map<Long, EquipmentSummaryVO.LineStat> byLine = new LinkedHashMap<>();
        for (EquipmentRowVO r : rows) {
            EquipmentSummaryVO.LineStat stat = byLine.computeIfAbsent(r.getLineId(), k -> {
                EquipmentSummaryVO.LineStat s = new EquipmentSummaryVO.LineStat();
                s.setLineId(r.getLineId());
                s.setLineName(r.getLineName());
                return s;
            });
            stat.setTotal(stat.getTotal() + 1);
            if ("正常".equals(r.getStatus())) {
                stat.setNormal(stat.getNormal() + 1);
            } else {
                stat.setAbnormal(stat.getAbnormal() + 1);
            }
        }
        vo.setByLine(new ArrayList<>(byLine.values()));
        return vo;
    }

    /** 状态分布（环形图数据） */
    public List<Map<String, Object>> statusDistribution() {
        List<EquipmentRowVO> rows = allEquipment();
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus() == null ? "未知" : r.getStatus(),
                        LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String s : VALID_STATUS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", s);
            item.put("value", counts.getOrDefault(s, 0L));
            result.add(item);
        }
        return result;
    }

    /* ============================================================
     * 私有工具
     * ============================================================ */

    private List<EquipmentRowVO> allEquipment() {
        EquipmentQuery q = new EquipmentQuery();
        q.normalize();
        Page<EquipmentRowVO> page = new Page<>(1, 500, false);
        return equipmentMapper.selectEquipmentPage(page, q).getRecords();
    }

    private long countBy(List<EquipmentRowVO> rows, String field, String value) {
        return rows.stream().filter(r -> value.equals("status".equals(field) ? r.getStatus() : r.getRiskLevel()))
                .count();
    }

    private List<RuntimePointVO> aggregateByDay(List<RuntimePointVO> raw) {
        Map<java.time.LocalDate, List<RuntimePointVO>> grouped = raw.stream()
                .filter(p -> p.getRecordTime() != null)
                .collect(Collectors.groupingBy(p -> p.getRecordTime().toLocalDate(),
                        java.util.TreeMap::new, Collectors.toList()));

        List<RuntimePointVO> out = new ArrayList<>(grouped.size());
        grouped.forEach((day, list) -> {
            RuntimePointVO avg = new RuntimePointVO();
            avg.setRecordTime(day.atStartOfDay());
            avg.setTemperature(average(list.stream().map(RuntimePointVO::getTemperature)));
            avg.setVibration(average(list.stream().map(RuntimePointVO::getVibration)));
            avg.setCurrent(average(list.stream().map(RuntimePointVO::getCurrent)));
            out.add(avg);
        });
        return out;
    }

    private BigDecimal average(Stream<BigDecimal> values) {
        List<BigDecimal> list = values.filter(Objects::nonNull).toList();
        if (list.isEmpty()) {
            return null;
        }
        BigDecimal sum = list.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }

    private void requireEquipment(Long id) {
        if (equipmentMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "设备不存在，id=" + id);
        }
    }

    /**
     * 产线下拉选项。
     * <p>
     * 只返回 <b>运行中</b>（{@code status = 1}）的产线：停用的产线不该再挂新设备，
     * 但历史设备的 {@code lineId} 仍指向它，所以不能物理删掉，只能在选项里过滤。
     * <p>
     * 按 {@code id} 升序，保证每次打开表单看到的下拉顺序一致。
     */
    public List<LineOptionVO> lineOptions() {
        return lineMapper.selectList(
                        new LambdaQueryWrapper<BaseProductionLine>()
                                .eq(BaseProductionLine::getStatus, 1)
                                .orderByAsc(BaseProductionLine::getId))
                .stream()
                .map(l -> new LineOptionVO(l.getId(), l.getLineName(), l.getWorkshopId()))
                .toList();
    }

    /** 产线下拉选项 */
    public record LineOptionVO(Long id, String lineName, Long workshopId) {
    }

    private void requireLine(Long lineId) {
        BaseProductionLine line = lineMapper.selectById(lineId);
        if (line == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产线不存在，lineId=" + lineId);
        }
    }

    private static void checkRange(BigDecimal v, double min, double max, String label) {
        if (v == null) {
            return;
        }
        if (v.doubleValue() < min || v.doubleValue() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    label + "超出合理范围（" + trimNum(min) + " ~ " + trimNum(max) + "）：" + v);
        }
    }

    private static String trimNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int countNonNull(BaseEquipment e) {
        Object[] fields = {e.getEquipmentCode(), e.getEquipmentName(), e.getLineId(), e.getEquipmentType(),
                e.getModel(), e.getManufacturer(), e.getHealthScore(), e.getRiskLevel(), e.getStatus(),
                e.getLastException()};
        return (int) Stream.of(fields).filter(Objects::nonNull).count();
    }

    /** 供 Controller 校验状态枚举值 */
    public static boolean isValidStatus(String s) {
        return s != null && VALID_STATUS.contains(s);
    }

    /** 供 Controller 校验风险等级枚举值 */
    public static boolean isValidRisk(String s) {
        return s != null && VALID_RISK.contains(s);
    }
}