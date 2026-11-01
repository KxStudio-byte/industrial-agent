package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备运行状态汇总（`GET /api/equipment/{id}/status` 与 `/api/equipment/status-summary` 共用）。
 * <p>
 * 字段设计原则：<b>状态判定与数值都给</b>。只给一个 {@code status} 字符串，
 * 前端画不出「离阈值还差多少」的进度条；只给数值，前端又要自己复算一遍阈值，
 * 两边各算一次必然对不上。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class EquipmentStatusVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long equipmentId;
    private String code;
    private String name;
    private String lineName;

    /** 设备状态：正常 / 预警 / 故障 / 离线 */
    private String status;
    /** 状态是否由实时数据判定为异常（温度或振动超阈值） */
    private Boolean abnormal;

    private Integer health;
    private String riskLevel;

    /* ---------- 实时值 ---------- */
    private Double temperature;
    private Double vibration;
    private Double current;

    /* ---------- 阈值（与判定逻辑同源，前端直接展示） ---------- */
    private Double temperatureLimit;
    private Double vibrationLimit;

    /** 数据采集时间 */
    private String recordTime;

    /** 距最近一次上报已过去多少秒；null 表示从未上报 */
    private Long staleSeconds;
    /** 数据是否已过期（超过 10 分钟没上报即视为过期，等价于「离线」） */
    private Boolean dataStale;

    /** 一句话结论，直接给前端展示 */
    private String message;
}