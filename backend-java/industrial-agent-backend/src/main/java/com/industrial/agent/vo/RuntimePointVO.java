package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运行数据点（实时值和历史趋势共用同一结构，前端 ECharts 直接绑）。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RuntimePointVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 上报时必填；查询结果里也会带出 */
    private Long equipmentId;

    /** 温度（℃） */
    private BigDecimal temperature;

    /** 振动（mm/s） */
    private BigDecimal vibration;

    /** 电流（A） */
    private BigDecimal current;

    /** 采集时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;
}