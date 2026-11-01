package com.industrial.agent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产计划行（列表用，带产线名与达成率）。
 * <p>
 * 字段对齐前端 {@code PlanItem}：{@code planDate / lineName / product / targetQty /
 * actualQty / achieve / status / aiExplanation}。
 * <p>
 * <b>为什么 achieve 在服务端算</b>：达成率 = 实际 / 计划 × 100，
 * 前端各页面都要用（列表、总览、OEE 看板），算法只有一份才不会出现
 * 列表显示 92.9% 而总览显示 93% 这种对不上的情况。
 */
@Data
public class PlanRowVO {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planDate;

    private Long lineId;

    private String lineName;

    private String product;

    /** 计划产量 */
    private Integer targetQty;

    /** 实际产量 */
    private Integer actualQty;

    /** 达成率(%)，保留 1 位；actualQty/targetQty 任一为空时返回 null */
    private BigDecimal achieve;

    private String status;

    private String shiftName;

    /** 计划工时(小时) */
    private BigDecimal planHours;

    /** 标准节拍(件/小时) */
    private Integer standardQtyPerHour;

    /** AI 说明 */
    private String aiExplanation;

    /** 已发生损失分钟数（停机+换线+小停机），列表页「累计损失」列 */
    private Integer lossMinutes;
}