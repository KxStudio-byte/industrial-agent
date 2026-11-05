package com.industrial.agent.controller;

import com.industrial.agent.common.Result;
import com.industrial.agent.dto.EnergyQuery;
import com.industrial.agent.security.PermissionConstants;
import com.industrial.agent.service.EnergyService;
import com.industrial.agent.vo.EnergyDetailVO;
import com.industrial.agent.vo.EnergySavingPlanVO;
import com.industrial.agent.vo.EnergySummaryVO;
import com.industrial.agent.vo.EnergyTopEquipmentVO;
import com.industrial.agent.vo.EnergyTrendVO;
import com.industrial.agent.vo.EnergyWorkshopVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 能源中心接口。
 * <p>
 * 全部为 GET + 只读查询，没有写操作 —— 能源数据来自采集系统，页面只做展示与分析。
 * 权限统一 {@code ENERGY:VIEW}；管理员 / 主管 / 操作员都有。
 * <p>
 * 路径与 {@code frontend/src/api/energy.ts} 一一对应，改路径必须同步改前端。
 */
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
public class EnergyController {

    private final EnergyService energyService;

    /**
     * 能耗总览 KPI（综合能耗 / 电 / 水 / 气 / 蒸汽 + 成本 + 单位产品能耗 + 每日节约）。
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<EnergySummaryVO> summary(@Valid EnergyQuery query) {
        return Result.success(energyService.summary(query));
    }

    /**
     * 能耗趋势曲线。
     * <p>
     * {@code granularity=day} → 按天上期对比；{@code granularity=hour} → 24 点今日 vs 昨日。
     */
    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<List<EnergyTrendVO>> trend(@Valid EnergyQuery query) {
        return Result.success(energyService.trend(query));
    }

    /**
     * 车间能耗对比（电耗，千kWh + 占比）。
     */
    @GetMapping("/workshops")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<List<EnergyWorkshopVO>> workshops(@Valid EnergyQuery query) {
        return Result.success(energyService.workshops(query));
    }

    /**
     * 能耗明细（车间 × 日期 × 介质）。
     */
    @GetMapping("/details")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<List<EnergyDetailVO>> details(@Valid EnergyQuery query) {
        return Result.success(energyService.details(query));
    }

    /**
     * 高耗能设备排行 TOP N。
     */
    @GetMapping("/top-equipment")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<List<EnergyTopEquipmentVO>> topEquipment(@Valid EnergyQuery query) {
        return Result.success(energyService.topEquipment(query));
    }

    /**
     * AI 节能优化方案列表（带状态计数）。
     * <p>
     * 不传 {@code page} 返回全量数组形态，传了则按 {@code page/size} 分页。
     */
    @GetMapping("/saving-plans")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<EnergySavingPlanVO.PlanListVO> savingPlans(@Valid EnergyQuery query) {
        return Result.success(energyService.plans(query));
    }

    /**
     * 单个节能方案详情（抽屉用）。
     */
    @GetMapping("/saving-plans/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ENERGY_VIEW + "')")
    public Result<EnergySavingPlanVO> savingPlanDetail(@PathVariable Long id) {
        EnergySavingPlanVO vo = energyService.planDetail(id);
        if (vo == null) {
            return Result.error(404, "方案不存在");
        }
        return Result.success(vo);
    }
}