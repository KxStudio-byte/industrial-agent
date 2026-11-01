package com.industrial.agent.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备总览 KPI（`GET /api/equipment/summary`）。
 * <p>
 * 结构与 {@code docs/API接口清单.md} §14 一致，前端 EquipmentOverview 的四张 KPI 卡直接绑。
 */
@Data
public class EquipmentSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;
    private long normal;
    private long warning;
    private long fault;
    private long offline;

    /** 在线 = 正常 + 预警 + 故障（离线不算） */
    private long online;
    /** 在线率，保留一位小数 */
    private double onlineRate;

    private long highRisk;
    private long midRisk;
    private long lowRisk;

    /** 按产线分布 */
    private List<LineStat> byLine;

    @Data
    public static class LineStat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long lineId;
        private String lineName;
        private long total;
        private long normal;
        /** 异常 = 预警 + 故障 + 离线 */
        private long abnormal;
    }
}