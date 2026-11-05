package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 能耗明细（能耗分析页底部表格）。
 * <p>
 * 一行 = 某车间某天的某介质。数据源是 {@code energy_meter} 里
 * {@code granularity='day'} 的车间级汇总行。
 * {@code change} 是该行与「同车间同介质前一天」的环比差值。
 */
@Data
@Builder
public class EnergyDetailVO {

    private Long id;
    /** 日期 */
    private LocalDate recordDate;
    private String workshop;
    private String medium;
    private BigDecimal value;
    private String unit;
    /** 环比差值（本行 - 前一天），如 +12.5 / -3.2 */
    private BigDecimal change;
}