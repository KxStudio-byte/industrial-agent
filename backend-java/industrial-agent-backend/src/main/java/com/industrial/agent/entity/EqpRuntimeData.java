package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备实时运行数据，对应表 {@code eqp_runtime_data}。
 * <p>
 * 注意：该表的 {@code uk_equipment_id} 是<b>唯一键</b> —— 一台设备只有一条最新记录，
 * 用 {@code ON DUPLICATE KEY UPDATE} 即可实现「上报即覆盖」。
 * 历史趋势请查 {@link EqpRuntimeHistory}。
 */
@Data
@TableName("eqp_runtime_data")
public class EqpRuntimeData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    /** 温度（℃） */
    private BigDecimal temperature;

    /** 振动（mm/s） */
    private BigDecimal vibration;

    /** 电流（A） */
    private BigDecimal current;

    /** 采集时间 */
    private LocalDateTime recordTime;

    private LocalDateTime updateTime;
}