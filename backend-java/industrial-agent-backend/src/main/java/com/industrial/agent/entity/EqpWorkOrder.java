package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 维修工单，对应表 {@code eqp_work_order}。
 * <p>
 * <b>设备名称不落库</b>：表里只有 {@code equipment_id}，名称、编号、产线都由查询时 JOIN
 * {@code base_equipment} 带出来。复制一份名称到工单表看着省事，但设备改名后历史工单
 * 显示的还是旧名字，对账时会对不上。
 * <p>
 * <b>逻辑删除</b>：和 {@link BaseEquipment} 一样，删除只做 UPDATE。
 * 工单是审计凭证，物理删除会让「这台设备修过几次」永远算不准。
 * <p>
 * <b>状态流转</b>：待处理 → 维修中 → 待验证 → 已完成，只允许单向推进，
 * 允许从维修中回退到待处理（派错人了）。这条规则在 {@code WorkOrderService} 里，
 * 不放在实体上 —— 实体只映射表结构。
 */
@Data
@TableName("eqp_work_order")
public class EqpWorkOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号，如 WO-20260907-018（唯一） */
    private String woNo;

    /** 设备ID，关联 base_equipment.id */
    private Long equipmentId;

    /** 故障类型，如「轴承异常」「电气故障」 */
    private String faultType;

    /** 优先级：紧急 / 高 / 中 / 低 */
    private String priority;

    /** 故障描述 */
    private String description;

    /** 创建来源：人工创建 / 设备运维Agent自动创建 / 故障预测Agent预警 … */
    private String source;

    /** 负责人ID，关联 sys_user.id */
    private Long assigneeId;

    /** 状态：待处理 / 维修中 / 待验证 / 已完成 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}