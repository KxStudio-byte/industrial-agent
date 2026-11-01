package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * OEE 分时段走势的一行（OEE 分析页「8:00—18:00 各时段 OEE」）。
 * <p>
 * 对齐前端 mock 的 {@code oeeByShift}：{@code time / factory / a / b / c}。
 * <p>
 * {@code time} 是「08:00—10:00」这种展示串而不是时间戳 —— 原型的横轴就是这个写法，
 * 且它是<b>两小时固定桶</b>，不是真实采样点，所以不需要精确到分钟。
 */
@Data
public class OeeShiftPointVO {

    /** 时段标签，如 "08:00—10:00" */
    private String time;

    /** 全厂 OEE(%) */
    private BigDecimal factory;

    /** A 产线 OEE(%) */
    private BigDecimal a;

    /** B 产线 OEE(%) */
    private BigDecimal b;

    /** C 产线 OEE(%) */
    private BigDecimal c;
}