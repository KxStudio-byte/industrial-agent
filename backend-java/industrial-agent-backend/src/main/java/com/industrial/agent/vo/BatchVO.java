package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次记录行（生产计划详情抽屉里的「批次明细」）。
 * <p>
 * 相比实体多带了设备名与不良率 —— 设备名要靠 join，不良率是派生值，
 * 都算在服务端，避免前端每个用到批次的地方各算一遍。
 */
@Data
public class BatchVO {

    private Long id;

    private Long planId;

    private String batchNo;

    private Long equipmentId;

    /** 设备名，无设备时为 "—" */
    private String equipmentName;

    /** 良品数 */
    private Integer goodQty;

    /** 不良数 */
    private Integer defectQty;

    /** 产出合计 */
    private Integer totalQty;

    /** 不良率(%) = 不良 / 产出 × 100 */
    private BigDecimal defectRate;

    /** 换型耗时(分钟) */
    private Integer changeoverMinutes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 时长(分钟) */
    private Integer durationMinutes;
}