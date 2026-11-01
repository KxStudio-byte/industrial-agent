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
 * 设备台账，对应表 {@code base_equipment}。
 * <p>
 * 逻辑删除：{@code is_deleted} 标记 {@code @TableLogic}，删除接口只做 UPDATE，不做物理 DELETE。
 * 这样设备被删掉后，历史工单 / 洞察里引用的 {@code equipment_id} 依然能追溯。
 */
@Data
@TableName("base_equipment")
public class BaseEquipment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属产线ID，关联 base_production_line.id */
    private Long lineId;

    /** 设备名称，如「3号注塑机」 */
    private String equipmentName;

    /** 设备编号，如 EQ-IM-003（唯一） */
    private String equipmentCode;

    /** 设备类型：注塑 / 冲压 / 装配 … */
    private String equipmentType;

    /** 型号，如 IM-220T */
    private String model;

    /** 制造厂商 */
    private String manufacturer;

    /** 健康度评分 0-100 */
    private Integer healthScore;

    /** 风险等级：高 / 中 / 低 */
    private String riskLevel;

    /** 最近异常描述 */
    private String lastException;

    /** 设备状态：正常 / 预警 / 故障 / 离线 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}