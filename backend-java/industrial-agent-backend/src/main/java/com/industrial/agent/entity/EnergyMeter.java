package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 能耗读表。
 * <p>
 * {@code granularity} 区分三种统计粒度，聚合时必须带条件，
 * 否则「车间日汇总」与「设备日明细」会被重复计入：
 * <ul>
 *   <li>{@code hour}   按小时，{@code recordHour} 有值 0-23</li>
 *   <li>{@code day}    按天 · 车间级汇总，{@code recordHour} 为空、{@code equipmentId} 为空</li>
 *   <li>{@code day_eq} 按天 · 设备级明细，{@code recordHour} 为空、{@code equipmentId} 有值</li>
 * </ul>
 */
@Data
@TableName("energy_meter")
public class EnergyMeter {

    /** 粒度：按小时 */
    public static final String GRANULARITY_HOUR = "hour";
    /** 粒度：按天 · 车间级汇总 */
    public static final String GRANULARITY_DAY = "day";
    /** 粒度：按天 · 设备级明细 */
    public static final String GRANULARITY_DAY_EQUIPMENT = "day_eq";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workshopId;
    private Long lineId;
    private Long equipmentId;

    /** 能源介质：电 / 水 / 气 / 蒸汽 */
    private String medium;

    /** 用量（按 unit 计量） */
    private BigDecimal value;

    /** 计量单位：kWh / t / Nm³ */
    private String unit;

    /** 统计粒度：hour / day / day_eq */
    private String granularity;

    private LocalDate recordDate;

    /** 小时 0-23，仅 granularity=hour 时有值 */
    private Integer recordHour;

    private LocalDateTime createTime;
}