package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 质量总览顶部 KPI，对应 {@code GET /api/quality/summary}。
 *
 * <p><b>{@code passRate} 与 {@code batchRate} 是两个不同口径，绝不合并：</b>
 * <table border="1">
 *   <tr><th>字段</th><th>中文</th><th>公式</th><th>分母</th></tr>
 *   <tr><td>yieldRate</td><td>良品率</td><td>Σgood / Σ(good+defect)</td><td>数量（件）</td></tr>
 *   <tr><td>passRate</td><td>合格率</td><td>Σgood / Σ(good+defect)</td><td>数量（件）</td></tr>
 *   <tr><td>batchRate</td><td>批次合格率</td><td>合格批次数 / 总批次数</td><td>批次（条）</td></tr>
 * </table>
 * 即便两者数值偶然相同（不良率分布均匀时常见），语义完全不同：一个是「件」，
 * 一个是「批」。合并会导致后续某一维度变化时另一维度跟着错。
 *
 * <p><b>为什么 delta 是字符串：</b>页面原样渲染「较昨日 -0.6pct」，交给后端拼好符号，
 * 前端不用再判断正负，避免拼出「较昨日 --0.6pct」。
 */
@Data
public class QualitySummaryVO {

    /** 良品率(%) = Σ良品数 / Σ(良品数+不良数) × 100，统计日口径 */
    private BigDecimal yieldRate;

    /** 良品率较昨日变化描述，如 "较昨日 -0.6pct"；昨日无数据时为 "昨日无数据" */
    private String yieldDelta;

    /** 缺陷数量（件），统计日口径。注意是 SUM(qty)，不是记录条数 */
    private Integer defects;

    /** 缺陷数说明，如 "今日 380 件，较昨日 +24 件" */
    private String defectsNote;

    /** 合格率(%) = Σ良品数 / Σ(良品数+不良数) × 100，数量口径 */
    private BigDecimal passRate;

    /** 批次合格率(%) = 合格批次数 / 总批次数 × 100，批次口径。合格判定见 {@code batchPassThreshold} */
    private BigDecimal batchRate;

    /** 批次合格率说明，如 "合格 12 / 共 12 批 · 不良率 ≤ 4% 判定合格" */
    private String batchRateNote;

    /** 本次统计采用的批次合格阈值(%)，回传前端便于在界面上标注口径 */
    private BigDecimal batchPassThreshold;

    /** 客诉数量（条），本周口径（周一至周日） */
    private Integer complaints;

    /** 客诉说明，如 "本周 4 条，处理中 2 条" */
    private String complaintsNote;

    /**
     * AI 质量分析摘要。
     * <p><b>本期固定为 {@code null}</b>：项目尚未接入 AI 服务（ai-service/ 为空目录，
     * 后端无 LLM 调用能力），按需求「不允许伪造 AI 分析结果」，
     * 这里不返回任何看似像 AI 输出的文本。前端收到 null 时展示空状态。
     */
    private String aiSummary;

    /** aiSummary 不可用的原因，前端可直接展示，避免用户以为功能坏了 */
    private String aiSummaryUnavailableReason;

    /** 统计口径说明，如 "良品率/缺陷数：2026-11-01 · 客诉：2026-10-26 ~ 2026-11-01" */
    private String scope;
}