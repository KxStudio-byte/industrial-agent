package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产总览顶部 KPI 卡。
 * <p>
 * 对齐原型：「今日计划产量 / 今日实际产量 / 计划达成率 / 今日 OEE」四张卡。
 * 每张卡都带一句对比说明（{@code *Note}），因为纯数字看不出好坏。
 */
@Data
public class ProductionOverviewVO {

    /** 今日计划产量 */
    private Integer planQty;

    /** 计划产量说明，如 "3 条产线 · 白班" */
    private String planNote;

    /** 今日实际产量 */
    private Integer actualQty;

    /** 实际产量说明，如 "较计划 -1,124 件" */
    private String actualNote;

    /** 计划达成率(%) */
    private BigDecimal achieveRate;

    /** 达成率说明，如 "较昨日 -2.1pct" */
    private String achieveNote;

    /** 今日 OEE(%) */
    private BigDecimal oee;

    /** OEE 说明 */
    private String oeeNote;

    /** 在产产线数 */
    private Integer lineCount;

    /** 达成率不低于 100% 的产线数 */
    private Integer onTrackLines;

    /** 延期产线数 */
    private Integer delayedLines;

    /** 数据日期 */
    private String statDate;
}