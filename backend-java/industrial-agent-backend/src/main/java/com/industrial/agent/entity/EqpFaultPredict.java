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
 * 设备故障预测，对应表 {@code eqp_fault_predict}。
 * <p>
 * 三个时间窗的概率由算法侧定期回写，前端设备列表的「故障预测」列直接读这里，
 * 不要在 Controller 里现算 —— 预测是离线模型给的，不是业务规则。
 */
@Data
@TableName("eqp_fault_predict")
public class EqpFaultPredict implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    /** 24 小时故障概率（0-100） */
    private BigDecimal h24;

    /** 7 天故障概率（0-100） */
    private BigDecimal d7;

    /** 30 天故障概率（0-100） */
    private BigDecimal d30;

    /** 预测模型版本 */
    private String modelVersion;

    private LocalDateTime createTime;
}