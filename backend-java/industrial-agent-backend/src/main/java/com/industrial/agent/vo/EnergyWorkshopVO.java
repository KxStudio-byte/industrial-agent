package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 车间能耗（画「车间能耗对比」横向柱状图 + 占比）。
 * <p>
 * {@code value} 固定按电耗（千kWh）统计，{@code ratio} 为占全厂电耗的百分比。
 */
@Data
@Builder
public class EnergyWorkshopVO {

    /** A车间 / B车间 / C车间 */
    private String workshop;
    /** 电耗（千kWh） */
    private BigDecimal value;
    /** 占比（%），0-100 */
    private BigDecimal ratio;
}