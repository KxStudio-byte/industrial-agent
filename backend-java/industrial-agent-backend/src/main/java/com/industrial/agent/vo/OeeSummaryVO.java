package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * OEE 顶部 KPI（OEE 分析页、生产总览页共用）。
 * <p>
 * 对齐前端 mock 的 {@code oeeSummary}。注意字段有<b>两套别名</b>
 * （{@code oee/value}、{@code oeeDelta/delta}、{@code qualityIndex/quality}）——
 * 那是前端两个页面历史上各用了一套名，这里都保留是为了不改前端类型定义就能直接替换数据源。
 * <p>
 * <b>delta 是字符串不是数字</b>：页面原样渲染「较昨日 -4.8pct」，
 * 换算成数字前端还得自己拼符号，容易拼出「较昨日 -−4.8pct」。
 */
@Data
public class OeeSummaryVO {

    /** OEE(%) */
    private BigDecimal oee;

    /** OEE(%) 别名 */
    private BigDecimal value;

    /** 较昨日变化描述，如 "较昨日 -4.8pct" */
    private String oeeDelta;

    /** 同上别名 */
    private String delta;

    /** 目标 OEE(%) */
    private BigDecimal target;

    /** 开动率(%) */
    private BigDecimal availability;

    /** 开动率目标说明，如 "目标 95%" */
    private String availabilityNote;

    /** 性能效率(%) */
    private BigDecimal performance;

    /** 性能效率目标说明 */
    private String performanceNote;

    /** 质量指数(%) */
    private BigDecimal qualityIndex;

    /** 质量指数目标说明 */
    private String qualityNote;

    /** 质量指数(%) 别名 */
    private BigDecimal quality;

    /** 底部说明，如 "OEE = 开动率 × 性能效率 × 质量指数 · 目标 90%，B产线低于目标 11.6pct" */
    private String note;

    /** 统计口径说明，如 "统计范围 2026-10-03 ~ 2026-11-01" */
    private String scope;
}