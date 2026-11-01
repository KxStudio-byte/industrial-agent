package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单行（联表投影），列表与详情共用。
 * <p>
 * 和 {@link EquipmentRowVO} 同样的分层理由：Mapper 只按结果集映射扁平行，
 * 前端契约的组装交给上层，SQL 与契约解耦。
 * <p>
 * 字段命名对齐原型页面的表头语义：
 * 工单编号 / 设备名称 / 故障类型 / 优先级 / 负责人 / 创建时间 / 状态 / 操作。
 */
@Data
public class WorkOrderRowVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 工单编号 ← wo_no */
    private String woNo;

    /* ---------- 设备（JOIN base_equipment） ---------- */

    private Long equipmentId;
    /** 设备名称 ← base_equipment.equipment_name */
    private String equipmentName;
    /** 设备编号 ← base_equipment.equipment_code，如 EQ-IM-003 */
    private String equipmentCode;
    /** 产线名 ← base_production_line.line_name（经设备表二跳） */
    private String lineName;
    /** 设备型号 ← base_equipment.model */
    private String model;
    /** 厂商 ← base_equipment.manufacturer */
    private String vendor;
    /** 设备当前状态：正常/预警/故障/离线 */
    private String equipmentStatus;
    /** 健康度 ← base_equipment.health_score */
    private Integer healthScore;
    /** 风险等级 ← base_equipment.risk_level */
    private String riskLevel;

    /* ---------- 工单本体 ---------- */

    /** 故障类型 */
    private String faultType;

    /** 优先级：紧急 / 高 / 中 / 低 */
    private String priority;

    /** 故障描述 */
    private String description;

    /** 创建来源 */
    private String source;

    /** 状态：待处理 / 维修中 / 待验证 / 已完成 */
    private String status;

    /* ---------- 负责人（JOIN sys_user） ---------- */

    private Long assigneeId;
    /** 负责人姓名 ← sys_user.real_name */
    private String assigneeName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}