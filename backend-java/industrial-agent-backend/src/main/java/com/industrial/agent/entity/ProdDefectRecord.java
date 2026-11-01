package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产缺陷明细表 {@code prod_defect_record}。
 *
 * <p>{@code qty} 是「缺陷件数」不是记录条数，统计时必须 {@code SUM(qty)}
 * 而不是 {@code COUNT(*)}。
 *
 * <p>关联（逻辑外键，库内无物理约束，与现有表规范一致）：
 * <ul>
 *   <li>{@code batch_id} → {@code prod_batch_record.id}</li>
 *   <li>{@code equipment_id} → {@code base_equipment.id}，可空</li>
 * </ul>
 */
@Data
@TableName("prod_defect_record")
public class ProdDefectRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次ID（逻辑外键 prod_batch_record.id） */
    private Long batchId;

    /** 责任设备ID（逻辑外键 base_equipment.id），可空 */
    private Long equipmentId;

    /** 缺陷类型：外观划伤 / 尺寸超差 / 装配漏装 / 色差 */
    private String defectType;

    /** 缺陷数量（件），不是记录条数 */
    private Integer qty;

    /** 发生日期 */
    private LocalDate recordDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}