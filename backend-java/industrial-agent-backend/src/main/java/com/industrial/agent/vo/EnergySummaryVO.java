package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 能耗总览 · KPI。
 * <p>
 * 字段名与原型和 {@code frontend/src/api/energy.ts} 一一对应：
 * 原型 5 张卡 = 综合能耗 / 电 / 水 / 气 / 蒸汽。
 */
@Data
@Builder
public class EnergySummaryVO {

    /** 综合能耗（t标煤） */
    private BigDecimal total;
    private String totalUnit;
    /** 综合能耗环比，如 "+1.2%" */
    private String totalNote;

    /** 用电（千kWh，已除 1000） */
    private BigDecimal electricity;
    private String electricityNote;

    /** 用水（t） */
    private BigDecimal water;
    private String waterNote;

    /** 用气（Nm³） */
    private BigDecimal gas;
    private String gasNote;

    /** 蒸汽（t） */
    private BigDecimal steam;
    private String steamNote;

    /** 能耗成本（元，今日） */
    private BigDecimal cost;
    /** 能耗成本环比，如 "+2.1%" */
    private String costNote;

    /** 单位产品能耗（kWh/件） */
    private BigDecimal unitConsumption;
    /** 单位产品能耗较昨日变化，如 "+0.28" */
    private String unitConsumptionNote;

    /** 每日可节约潜力（元），由未执行方案月节约 / 30 推算 */
    private BigDecimal dailySavingPotential;
    /** 该数值的口径说明 */
    private String dailySavingNote;

    /** AI 提示（规则引擎生成，非大模型） */
    private String aiHint;
    /** AI 提示的数据支撑说明 */
    private String aiHintBasis;

    /** 统计口径 */
    private String scope;

    /** 介质明细（画「今日 vs 昨日（分介质）」柱状图） */
    private List<MediumRow> mediums;

    /** 单介质一行 */
    @Data
    @Builder
    public static class MediumRow {
        /** 电 / 水 / 气 / 蒸汽 */
        private String medium;
        /** 今日用量（按 unit） */
        private BigDecimal today;
        /** 昨日用量（按 unit） */
        private BigDecimal yesterday;
        private String unit;
        /** 环比文案，如 "+12.4%" */
        private String delta;
    }
}