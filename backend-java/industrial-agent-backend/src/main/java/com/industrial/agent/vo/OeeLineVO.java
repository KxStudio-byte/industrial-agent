package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 一条产线的 OEE 分解（生产总览 / OEE 分析共用）。
 * <p>
 * 对齐前端 mock 的 {@code oeeByLine}：{@code lineName / oee / delta / availability /
 * performance / qualityIndex / downtime / lossBottleneck}。
 * <p>
 * <b>OEE 三个分量必须同时返回</b>：只给一个 OEE 数字，用户看不出是停机太多还是良率太低。
 * 三个分量都在这里，前端才能直接画成「89.4% = 95.1% × 95.8% × 98.1%」。
 */
@Data
public class OeeLineVO {

    private Long lineId;

    private String lineName;

    /** OEE(%) */
    private BigDecimal oee;

    /** 较昨日变化(百分点)，带符号，如 "-0.8" / "+0.4" */
    private String delta;

    /** 开动率(%) */
    private BigDecimal availability;

    /** 性能效率(%) */
    private BigDecimal performance;

    /** 质量指数(%) */
    private BigDecimal qualityIndex;

    /** 损失时长，如 "28 分钟" */
    private String downtime;

    /** 主要损失来源（瓶颈），由损失明细里时长最大的类型决定 */
    private String lossBottleneck;

    /** 计划产量 */
    private Integer targetQty;

    /** 实际产量 */
    private Integer actualQty;

    /** 达成率(%) */
    private BigDecimal achieve;
}