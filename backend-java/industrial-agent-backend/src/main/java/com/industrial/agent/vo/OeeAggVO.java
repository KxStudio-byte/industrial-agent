package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * OEE 三指标的<b>集合口径</b>聚合结果。
 * <p>
 * 这不是给前端展示用的 VO，而是 Mapper 查出来、Service 再算成百分比的中间结果。
 * <b>为什么要按"集合"聚合而不是把每天的 OEE 平均</b>：
 * 平均每日 OEE 会让「小产量的一天」和「满产的一天」权重相同，
 * 结果与真实产能利用率不符。正确做法是先把分子分母分别累加，再一次性相除。
 */
@Data
public class OeeAggVO {

    /** 产线ID（仅分组查询时有值） */
    private Long lineId;

    /** 产线名（仅分组查询时有值） */
    private String lineName;

    /** 计划工时合计(分钟) */
    private BigDecimal planMinutes;

    /** 停机合计(分钟) = 设备停机 + 换线时间 + 小停机 */
    private BigDecimal downMinutes;

    /** 可用工时合计(分钟) = 计划工时 - 停机 */
    private BigDecimal availableMinutes;

    /** 理论产能合计(件) = 标准节拍 × 可用工时 ÷ 60 */
    private BigDecimal capacityQty;

    /** 产出合计(件) = 良品 + 不良 */
    private BigDecimal outputQty;

    /** 良品合计(件) */
    private BigDecimal goodQty;

    /** 不良合计(件) */
    private BigDecimal defectQty;

    /** 计划产量合计(件) */
    private BigDecimal targetQty;

    /** 实际产量合计(件)，来自 prod_plan.actual_qty */
    private BigDecimal actualQty;
}