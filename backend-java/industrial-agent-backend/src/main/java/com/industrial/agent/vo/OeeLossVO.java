package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 损失明细行（OEE 分析页「损失 TOP 榜」）。
 * <p>
 * 对齐前端 mock 的 {@code oeeLosses}：{@code lossType / trigger / duration / impact /
 * lineName / equipment}。其中 {@code trigger} 取库里的 {@code attribution}，
 * {@code duration} 是带单位的字符串（"146 分钟"），因为前端原样展示这几个字段。
 */
@Data
public class OeeLossVO {

    private Long id;

    /** 损失类型：设备停机/换线时间/质量损失/速度降低/小停机/其他 */
    private String lossType;

    /** 归因，如「故障 / 更换模具」 */
    private String trigger;

    /** 时长（分钟，数值，便于前端排序） */
    private Integer minutes;

    /** 时长展示串，如 "146 分钟" */
    private String duration;

    /** 影响（百分点），带符号串，如 "-2.0pct" */
    private String impact;

    /** 影响（百分点，数值） */
    private BigDecimal impactValue;

    private Long lineId;

    private String lineName;

    /** 关联设备名，无设备时为 "—" */
    private String equipment;

    /** 补充说明 */
    private String description;

    /** 损失发生时刻（仅明细查询有值，列表展示用不上） */
    private java.time.LocalDateTime occurTime;
}