package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单详情 VO（前端「工单详情」抽屉的数据源）。
 * <p>
 * 原型要求「工单信息与关联设备一并展示，无需再打开设备弹窗」，
 * 所以这里把设备本体和最新运行数据一起带出来 —— 维修工到场前想知道温度/振动是不是还在飙，
 * 少点一次跳转就少一次现场等待。
 * <p>
 * 两层结构：{@code workOrder} 是本工单字段，{@code equipment} 是冗余进来的关联设备快照。
 * 前端按这个结构直接渲染，不需要再发第二个请求。
 */
@Data
public class WorkOrderDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工单本体（扁平字段，前端抽屉上半部分） */
    private WorkOrderRowVO workOrder;

    /** 关联设备快照（前端抽屉下半部分「设备信息」） */
    private Equipment equipment;

    /** 关联设备的最大信息量 */
    @Data
    public static class Equipment implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;

        /** 设备名称 */
        private String name;

        /** 设备编号 EQ-IM-003 */
        private String code;

        /** 型号 */
        private String model;

        /** 厂商 */
        private String vendor;

        /** 所属产线 */
        private String lineName;

        /** 设备状态：正常/预警/故障/离线 */
        private String status;

        /** 健康度 0-100 */
        private Integer health;

        /** 风险等级 */
        private String riskLevel;

        /* ---------- 最新运行数据 ← eqp_runtime_data ---------- */

        /** 温度 ℃ */
        private BigDecimal temperature;

        /** 振动 mm/s */
        private BigDecimal vibration;

        /** 电流 A */
        private BigDecimal current;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime recordTime;

        /* ---------- 故障预测 ← eqp_fault_predict ---------- */

        /** 24 小时内故障概率 % */
        private BigDecimal h24;

        /** 7 天 */
        private BigDecimal d7;

        /** 30 天 */
        private BigDecimal d30;

        /** 最近 AI 诊断 */
        private String diagnosis;
    }
}