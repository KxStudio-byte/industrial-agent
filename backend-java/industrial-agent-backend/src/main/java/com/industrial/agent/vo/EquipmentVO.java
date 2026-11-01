package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.industrial.agent.entity.BaseEquipment;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备对外 VO，结构与前端 {@code types/index.ts:Equipment} 一一对应。
 * <pre>
 * { "id":3, "code":"EQ-IM-003", "name":"3号注塑机", "type":"注塑", "model":"IM-220T",
 *   "vendor":"海天", "lineName":"A产线", "status":"预警", "health":72, "riskLevel":"高",
 *   "lastException":"...", "temperature":68.4, "vibration":7.8, "current":42.6,
 *   "predict":{"h24":12,"d7":45,"d30":68}, "diagnosis":"..." }
 * </pre>
 * {@code predict} 之所以是嵌套对象：前端卡片直接绑 {@code row.predict.h24}，
 * 扁平化后模板要全改。
 */
@Data
public class EquipmentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private String type;
    private String model;
    private String vendor;
    private Long lineId;
    private String lineName;
    private String status;
    private Integer health;
    private String riskLevel;
    private String lastException;

    /** 实时数据（eqp_runtime_data）；设备从未上报时有值但内容为 null */
    private BigDecimal temperature;
    private BigDecimal vibration;
    private BigDecimal current;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    /** 故障预测（eqp_fault_predict），无预测数据时各项为 null 而不是 0，避免误导 */
    private Predict predict;

    /** 最近一条 AI 诊断 */
    private String diagnosis;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Data
    public static class Predict implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 24 小时故障概率（%） */
        private BigDecimal h24;
        /** 7 天故障概率（%） */
        private BigDecimal d7;
        /** 30 天故障概率（%） */
        private BigDecimal d30;
        /** 预测模型版本 */
        private String modelVersion;
    }

    /** 由联表行组装 */
    public static EquipmentVO from(EquipmentRowVO row) {
        EquipmentVO vo = new EquipmentVO();
        vo.setId(row.getId());
        vo.setCode(row.getCode());
        vo.setName(row.getName());
        vo.setType(row.getType());
        vo.setModel(row.getModel());
        vo.setVendor(row.getVendor());
        vo.setLineId(row.getLineId());
        vo.setLineName(row.getLineName());
        vo.setStatus(row.getStatus());
        vo.setHealth(row.getHealth());
        vo.setRiskLevel(row.getRiskLevel());
        vo.setLastException(row.getLastException());
        vo.setTemperature(row.getTemperature());
        vo.setVibration(row.getVibration());
        vo.setCurrent(row.getCurrent());
        vo.setRecordTime(row.getRecordTime());
        vo.setDiagnosis(row.getDiagnosis());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());

        // 三个预测值全为空时不造一个空对象，前端用 optional chaining 判断
        if (row.getH24() != null || row.getD7() != null || row.getD30() != null) {
            Predict p = new Predict();
            p.setH24(row.getH24());
            p.setD7(row.getD7());
            p.setD30(row.getD30());
            p.setModelVersion(row.getPredictModel());
            vo.setPredict(p);
        }
        return vo;
    }

    /**
     * 只靠设备主表组装（新增 / 修改后的返回体用）。
     * <p>
     * 这种情况下产线名、实时值、预测值都不在手上，
     * <b>调用方应该再查一次详情</b>把完整数据返回给前端，避免前端拿到一半的字段去做局部刷新。
     */
    public static EquipmentVO skeleton(BaseEquipment e) {
        EquipmentVO vo = new EquipmentVO();
        vo.setId(e.getId());
        vo.setCode(e.getEquipmentCode());
        vo.setName(e.getEquipmentName());
        vo.setType(e.getEquipmentType());
        vo.setModel(e.getModel());
        vo.setVendor(e.getManufacturer());
        vo.setLineId(e.getLineId());
        vo.setStatus(e.getStatus());
        vo.setHealth(e.getHealthScore());
        vo.setRiskLevel(e.getRiskLevel());
        vo.setLastException(e.getLastException());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}