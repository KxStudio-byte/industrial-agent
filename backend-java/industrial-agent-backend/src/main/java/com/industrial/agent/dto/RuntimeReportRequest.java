package com.industrial.agent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运行数据上报请求（`POST /api/equipment/{id}/runtime`）。
 * <p>
 * 三路采集值只做「非负 + 上限」的范围校验，不做强制必填：
 * 现场有些采集网关只上报温度，不接受这种差异就没法对接。
 * 真正决定「哪些值算异常」的是 {@code EquipmentService} 里的阈值，不是这里的校验。
 */
@Data
public class RuntimeReportRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 温度（℃），范围 0-200 */
    private BigDecimal temperature;

    /** 振动（mm/s），范围 0-50 */
    private BigDecimal vibration;

    /** 电流（A），范围 0-1000 */
    private BigDecimal current;

    /** 采集时间；不传则用服务器当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;
}