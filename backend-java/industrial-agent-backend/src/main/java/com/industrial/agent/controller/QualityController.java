package com.industrial.agent.controller;

import com.industrial.agent.common.Result;
import com.industrial.agent.dto.QualityQuery;
import com.industrial.agent.security.PermissionConstants;
import com.industrial.agent.service.QualityService;
import com.industrial.agent.vo.QualityDefectVO;
import com.industrial.agent.vo.QualitySummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量中心接口。
 *
 * <p>本期只有两个只读端点，对应前端「质量总览」页：
 * <ul>
 *   <li>{@code GET /api/quality/summary} — 顶部 4 个 KPI</li>
 *   <li>{@code GET /api/quality/defects} — 缺陷 Top + 产线/产品/设备三维度</li>
 * </ul>
 *
 * <p><b>两个端点都只返回真实统计数据，不返回任何 AI 生成的归因结论。</b>
 * 接口文档里「根因分析」的 AI 部分依赖 ai-service（尚未部署），
 * 后端宁可返回 {@code aiSummary: null} + 不可用原因，也不伪造文本。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;

    /**
     * 质量总览 KPI。
     *
     * @param query 可选 {@code date}（yyyy-MM-dd，默认今天）、{@code days}（默认 30）
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.QUALITY_VIEW + "')")
    public Result<QualitySummaryVO> summary(@Validated QualityQuery query) {
        return Result.success(qualityService.summary(query));
    }

    /**
     * 缺陷统计：Top 列表 + 产线/产品/设备三个维度。
     *
     * @param query 可选 {@code date}（窗口结束日，默认今天）、{@code days}（窗口天数，默认 30）
     */
    @GetMapping("/defects")
    @PreAuthorize("hasAuthority('" + PermissionConstants.QUALITY_VIEW + "')")
    public Result<QualityDefectVO> defects(@Validated QualityQuery query) {
        return Result.success(qualityService.defects(query));
    }
}