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
 * 设备运行数据历史，对应表 {@code eqp_runtime_history}。
 * <p>
 * 为什么单独一张表：{@code eqp_runtime_data} 上有 {@code uk_equipment_id} 唯一键，
 * 只能存「最新一条」，装不下趋势图需要的时序数据。
 */
@Data
@TableName("eqp_runtime_history")
public class EqpRuntimeHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    private BigDecimal temperature;

    private BigDecimal vibration;

    private BigDecimal current;

    /** 采集时间 */
    private LocalDateTime recordTime;
}