package com.industrial.agent.controller;

import com.industrial.agent.common.PageResult;
import com.industrial.agent.common.Result;
import com.industrial.agent.common.ResultCode;
import com.industrial.agent.dto.EquipmentCreateRequest;
import com.industrial.agent.dto.EquipmentQuery;
import com.industrial.agent.dto.EquipmentUpdateRequest;
import com.industrial.agent.dto.RuntimeReportRequest;
import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.security.PermissionConstants;
import com.industrial.agent.service.EquipmentService;
import com.industrial.agent.vo.EquipmentStatusVO;
import com.industrial.agent.vo.EquipmentSummaryVO;
import com.industrial.agent.vo.EquipmentTrendVO;
import com.industrial.agent.vo.EquipmentVO;
import com.industrial.agent.vo.RuntimePointVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 设备中心接口。
 * <pre>
 * 1. 列表   GET    /api/equipment                 查询参数见 EquipmentQuery
 * 2. 详情   GET    /api/equipment/{id}
 * 3. 新增   POST   /api/equipment
 * 4. 修改   PUT    /api/equipment/{id}
 * 5. 删除   DELETE /api/equipment/{id}
 * 6. 状态   GET    /api/equipment/{id}/status
 *           GET    /api/equipment/status-summary
 * 7. 运行   GET    /api/equipment/{id}/runtime   最新值
 *           GET    /api/equipment/{id}/trend     趋势
 *           POST   /api/equipment/{id}/runtime   上报
 * 附加      GET    /api/equipment/summary、/status-distribution
 * </pre>
 * <p>
 * <b>路径顺序有坑</b>：{@code /{id}} 这类路径变量会吞掉任何单段路径，
 * 所以 {@code /summary}、{@code /status-summary} 这些字面量路径必须能匹配到，
 * Spring 的 {@code AntPathMatcher} 会优先选「更具体」的模式，字面量优先于变量，这一点是安全的；
 * 但 {@code id} 用 {@code Long} 类型时，传 "summary" 会在类型转换阶段就报 400 而不是 404 ——
 * 本文常量路径都写在 {@code /{id}} 之前，靠顺序+具体度双重保证。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    /* ============================================================
     * 1. 设备列表（分页 + 条件筛选）
     * ============================================================ */

    /**
     * 设备列表。
     * <p>
     * <b>双模式返回</b>：不传 {@code page} 返回数组；传了返回
     * {@code {total,page,size,pages,list}}。
     * 用 {@code Result<Object>} 而不是两个重载方法，是因为路径相同，Spring 无法按
     * 「有没有传 page」分派到不同方法 —— 那需要两个 @GetMapping 相同的映射，启动就冲突。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<Object> list(@Valid EquipmentQuery query) {
        Object data = equipmentService.list(query);
        String msg = data instanceof PageResult<?> p
                ? String.format("共 %d 条，第 %d/%d 页", p.getTotal(), p.getPage(), Math.max(p.getPages(), 1))
                : "共 " + ((List<?>) data).size() + " 条";
        return Result.success(data, msg);
    }

    /* ============================================================
     * 2. 设备详情
     * ============================================================ */

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<EquipmentVO> detail(@PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id) {
        return Result.success(equipmentService.detail(id));
    }

    /* ============================================================
     * 3. 新增设备
     * ============================================================ */

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_CREATE + "')")
    public Result<EquipmentVO> create(@RequestBody @Valid EquipmentCreateRequest request) {
        EquipmentVO vo = equipmentService.create(request);
        log.info("新增设备成功 id={} code={}", vo.getId(), vo.getCode());
        return Result.success(vo, "设备创建成功");
    }

    /* ============================================================
     * 4. 修改设备
     * ============================================================ */

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_UPDATE + "')")
    public Result<EquipmentVO> update(
            @PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id,
            @RequestBody @Valid EquipmentUpdateRequest request) {
        return Result.success(equipmentService.update(id, request), "设备修改成功");
    }

    /* ============================================================
     * 5. 删除设备（逻辑删除）
     * ============================================================ */

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_DELETE + "')")
    public Result<Void> delete(@PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id) {
        equipmentService.delete(id);
        return Result.success(null, "设备删除成功");
    }

    /* ============================================================
     * 6. 状态查询
     * ============================================================ */

    /** 单台设备的运行状态判定 */
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<EquipmentStatusVO> status(
            @PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id) {
        return Result.success(equipmentService.status(id));
    }

    /**
     * 全部设备的状态快照。
     * <p>
     * 大屏 / 工位牌每 30 秒轮询一次，所以不走分页 —— 分页反而让前端要自己拼。
     * 上限 500 台由 Service 兜住，超过时应该改用增量接口。
     */
    @GetMapping("/status-summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<List<EquipmentStatusVO>> statusSummary(
            @RequestParam(value = "lineName", required = false) String lineName,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(equipmentService.statusSummary(lineName, status));
    }

    /* ============================================================
     * 7. 运行数据
     * ============================================================ */

    /** 最新一条运行数据 */
    @GetMapping("/{id}/runtime")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<RuntimePointVO> latestRuntime(
            @PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id) {
        return Result.success(equipmentService.latestRuntime(id));
    }

    /**
     * 运行数据趋势。
     *
     * @param granularity hour（默认）/ day
     * @param hours       按小时粒度时的回溯小时数，默认 24，最大 720
     * @param days        按天粒度时的回溯天数，默认 7，最大 90
     */
    @GetMapping("/{id}/trend")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<EquipmentTrendVO> trend(
            @PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id,
            @RequestParam(value = "granularity", required = false, defaultValue = "hour") String granularity,
            @RequestParam(value = "hours", required = false)
            @Min(value = 1, message = "hours 最小为 1") @Max(value = 720, message = "hours 最大为 720") Integer hours,
            @RequestParam(value = "days", required = false)
            @Min(value = 1, message = "days 最小为 1") @Max(value = 90, message = "days 最大为 90") Integer days) {
        return Result.success(equipmentService.trend(id, granularity, hours, days));
    }

    /**
     * 上报运行数据（采集网关 / 现场终端）。
     * <p>
     * 返回的是上报后的状态判定结果 —— 采集端拿到 {@code abnormal=true} 时
     * 可以直接驱动现场的声光报警，不用再调一次状态接口。
     */
    @PostMapping("/{id}/runtime")
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.EQUIPMENT_REPORT_RUNTIME + "','"
            + PermissionConstants.EQUIPMENT_UPDATE + "')")
    public Result<EquipmentStatusVO> reportRuntime(
            @PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id,
            @RequestBody @Valid RuntimeReportRequest request) {
        return Result.success(equipmentService.reportRuntime(id, request), "运行数据上报成功");
    }

    /** 批量补录历史点（数据修复 / 断网补传） */
    @PostMapping("/{id}/runtime/history")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_UPDATE + "')")
    public Result<Map<String, Object>> backfill(
            @PathVariable("id") @Min(value = 1, message = "设备 ID 必须为正整数") Long id,
            @RequestBody List<RuntimePointVO> points) {
        int n = equipmentService.backfillHistory(id, points);
        return Result.success(Map.of("inserted", n), "补录成功，共 " + n + " 条");
    }

    /* ============================================================
     * 附加：设备总览 KPI（前端 EquipmentOverview 页用）
     * ============================================================ */

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<EquipmentSummaryVO> summary() {
        return Result.success(equipmentService.summary());
    }

    @GetMapping("/status-distribution")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<List<Map<String, Object>>> statusDistribution() {
        return Result.success(equipmentService.statusDistribution());
    }

    /* ============================================================
     * 附加：枚举字典（前端下拉框选项，避免前后端各写一份硬编码）
     * ============================================================ */

    /**
     * 枚举字典 + 产线列表。
     * <p>
     * 新增设备时必须提交真实的 {@code lineId}（{@link EquipmentCreateRequest#lineId} 是 {@code @NotNull}），
     * 所以这里把产线一起返回，前端下拉框直接用 {@code lines[].id} 作为值、
     * {@code lines[].lineName} 作为显示文本 —— 避免前端按「A产线」猜 ID。
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('" + PermissionConstants.EQUIPMENT_VIEW + "')")
    public Result<Map<String, Object>> options() {
        return Result.success(Map.of(
                "status", List.of("正常", "预警", "故障", "离线"),
                "riskLevel", List.of("高", "中", "低"),
                "sortField", List.of("id", "code", "health", "updateTime"),
                "sortOrder", List.of("asc", "desc"),
                "lines", equipmentService.lineOptions()
        ));
    }

    /** 参数守卫：给 Service 兜底之外的直接校验（保留给后续扩展） */
    @SuppressWarnings("unused")
    private static void require(boolean ok, String msg) {
        if (!ok) {
            throw new BusinessException(ResultCode.PARAM_ERROR, msg);
        }
    }
}