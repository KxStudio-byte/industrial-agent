package com.industrial.agent.controller;

import com.industrial.agent.common.Result;
import com.industrial.agent.dto.PlanCreateRequest;
import com.industrial.agent.dto.PlanQuery;
import com.industrial.agent.service.ProductionService;
import com.industrial.agent.vo.OeeLineVO;
import com.industrial.agent.vo.OeeLossVO;
import com.industrial.agent.vo.OeeSummaryVO;
import com.industrial.agent.vo.PlanDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.industrial.agent.security.PermissionConstants.PRODUCTION_EDIT;
import static com.industrial.agent.security.PermissionConstants.PRODUCTION_VIEW;

/**
 * 生产运营接口（生产总览 / 生产计划 / OEE 分析）。
 * <p>
 * 基路径 {@code /api/production}，全部只读 —— 生产计划与批次是 MES 侧下发的，
 * 本平台只做分析展示，不在这里改计划量。需要写入时另起接口并单独授权。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;

    /* ==================== 生产总览 ==================== */

    /**
     * 生产总览：KPI + 产线卡片。
     * <pre>
     * GET /api/production/overview
     * → { "kpi": {...}, "lines": [...] }
     * </pre>
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<Map<String, Object>> overview() {
        return Result.success(productionService.overview());
    }

    /* ==================== 生产计划 ==================== */

    /**
     * 生产计划列表。
     * <pre>
     * GET /api/production/plans?status=已延期&amp;lineName=A产线&amp;startDate=2026-10-01&amp;page=1&amp;size=10
     * </pre>
     * 不传 {@code page}/{@code size} 返回数组，传了返回 {@code {total,page,size,pages,list}}。
     */
    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<Object> plans(@Valid PlanQuery query) {
        return Result.success(productionService.listPlans(query));
    }

    /** 生产计划详情：主体 + 批次 + 损失，一次返回 */
    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<PlanDetailVO> planDetail(@PathVariable Long id) {
        return Result.success(productionService.planDetail(id));
    }

    /** 生产计划下拉选项（状态/班次/产线） */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<Map<String, Object>> options() {
        return Result.success(productionService.options());
    }

    /**
     * 新建生产计划。
     * <pre>
     * POST /api/production/plans
     * body: { planDate:"2026-11-02", lineId:1, productName:"外壳组件 A-220",
     *         targetQty:6240, planHours:10.0, standardQtyPerHour:660, shiftName:"白班" }
     * → { plan:{...}, batches:[], losses:[] }
     * </pre>
     * 实际产量固定为 0、状态固定为「待执行」，由执行过程回填。
     */
    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('" + PRODUCTION_EDIT + "')")
    public Result<PlanDetailVO> createPlan(@Valid @RequestBody PlanCreateRequest req) {
        return Result.success(productionService.createPlan(req), "生产计划已创建");
    }

    /* ==================== OEE ==================== */

    /**
     * OEE 顶部 KPI。
     *
     * @param days 统计窗口天数，默认 30
     */
    @GetMapping("/oee/summary")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<OeeSummaryVO> oeeSummary(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long lineId) {
        return Result.success(productionService.oeeSummary(days, lineId));
    }

    /** 分产线 OEE（开动率/性能效率/质量指数三分解 + 瓶颈） */
    @GetMapping("/oee/by-line")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<List<OeeLineVO>> oeeByLine(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long lineId) {
        return Result.success(productionService.oeeByLine(days, lineId));
    }

    /** OEE 损失 TOP 榜 */
    @GetMapping("/oee/losses")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<List<OeeLossVO>> losses(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long lineId,
            @RequestParam(required = false) Integer limit) {
        return Result.success(productionService.lossTop(days, lineId, limit));
    }

    /** 损失按类型汇总（饼图） */
    @GetMapping("/oee/loss-types")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<List<Map<String, Object>>> lossTypes(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long lineId) {
        return Result.success(productionService.lossByType(days, lineId));
    }

    /**
     * 分时段产出走势。
     * <p>
     * <b>不叫 /oee/by-shift</b>：它返回的是「产出占比 × 良率」相对值，不是绝对 OEE，
     * 路径上标成 oee 会让人误当成绝对指标。
     */
    @GetMapping("/output/by-shift")
    @PreAuthorize("hasAuthority('" + PRODUCTION_VIEW + "')")
    public Result<Map<String, Object>> outputByShift(
            @RequestParam(required = false) Integer days) {
        return Result.success(productionService.outputByShift(days));
    }
}