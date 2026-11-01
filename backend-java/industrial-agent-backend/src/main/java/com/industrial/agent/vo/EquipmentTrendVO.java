package com.industrial.agent.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备运行趋势（`GET /api/equipment/{id}/trend`）。
 */
@Data
public class EquipmentTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long equipmentId;

    /** 粒度：hour / day */
    private String granularity;

    /** 统计区间 */
    private String from;
    private String to;

    private int pointCount;

    /** 聚合后的点，按时间升序 */
    private List<RuntimePointVO> points;

    /** 该区间是否还有更早的历史数据可查（前端决定要不要显示「加载更早」） */
    private Boolean hasMore;
}