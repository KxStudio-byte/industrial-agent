package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备行（联表投影），列表与详情共用。
 * <p>
 * 字段名严格对齐前端 {@code frontend/src/types/index.ts:Equipment}，
 * 但实时值 / 预测值用的是<b>扁平字段</b>（{@code temperature} 而不是 {@code runtime.temperature}），
 * 由 {@link EquipmentVO} 负责组装成前端的嵌套结构。
 * <p>
 * 拆成「扁平行」和「嵌套 VO」两层的原因：Mapper 只需要按 SQL 结果集映射，
 * 不需要依赖任何业务结构；改前端契约时只动 VO，SQL 一行不改。
 */
@Data
public class EquipmentRowVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 设备编号 ← equipment_code */
    private String code;

    /** 设备名称 ← equipment_name */
    private String name;

    /** 设备类型 ← equipment_type */
    private String type;

    /** 型号 */
    private String model;

    /** 厂商 ← manufacturer */
    private String vendor;

    private Long lineId;

    /** 产线名 ← base_production_line.line_name（JOIN） */
    private String lineName;

    /** 状态：正常 / 预警 / 故障 / 离线 */
    private String status;

    /** 健康度 ← health_score */
    private Integer health;

    /** 风险等级 ← risk_level */
    private String riskLevel;

    /** 最近异常 ← last_exception */
    private String lastException;

    /* ---------- 实时数据 ← eqp_runtime_data ---------- */
    private BigDecimal temperature;
    private BigDecimal vibration;
    private BigDecimal current;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    /* ---------- 故障预测 ← eqp_fault_predict ---------- */
    private BigDecimal h24;
    private BigDecimal d7;
    private BigDecimal d30;
    private String predictModel;

    /* ---------- 最近 AI 诊断 ← ai_insight.ai_diagnosis ---------- */
    private String diagnosis;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}