package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 能耗趋势（画「能耗趋势」折线图）。
 * <p>
 * {@code current} 是统计窗口内的实际值；{@code last} 是<b>同比窗口</b>
 * （同长度、紧邻当前窗口之前的历史段），不是去年同期。
 * 前端图例文案为「本期 / 上期」，与这里的口径一致。
 */
@Data
@Builder
public class EnergyTrendVO {

    /** 横轴标签，如 "10-30" 或 "08:00" */
    private String label;
    /** 本期用量 */
    private BigDecimal current;
    /** 上期用量 */
    private BigDecimal last;
}