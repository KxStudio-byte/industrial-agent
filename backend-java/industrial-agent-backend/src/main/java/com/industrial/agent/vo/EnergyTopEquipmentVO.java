package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 高耗能设备 TOP N（能耗分析页排行）。
 * <p>
 * 数据源是 {@code energy_meter} 里 {@code granularity='day_eq'} 的设备级日明细，
 * 窗口内按设备求和。{@code change} 为「窗口后半段 vs 前半段」的环比，
 * 用来标记哪台设备正在变差（不能拿窗口内合计和前一天比，口径不同）。
 */
@Data
@Builder
public class EnergyTopEquipmentVO {

    /** 设备编号，如 EQ-A01 */
    private String equipmentCode;
    private String equipmentName;
    private String workshop;
    /** 窗口内累计耗电（kWh） */
    private BigDecimal value;
    /** 窗口后半段较前半段的变化（%），正数表示变耗 */
    private BigDecimal change;
    /** 警戒标记：true 表示环比超过阈值，前端给红点 */
    private Boolean abnormal;
}