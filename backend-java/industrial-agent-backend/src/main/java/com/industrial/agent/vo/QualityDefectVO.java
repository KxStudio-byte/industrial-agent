package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 缺陷统计，对应 {@code GET /api/quality/defects}。
 *
 * <p>字段名与含义严格对齐接口文档，不做任何重命名，前端可直接消费。
 *
 * <p><b>口径（缺陷 Top / 分维度统计都是「近 N 天」窗口，不是单日）：</b>
 * <ul>
 *   <li>{@code top[i].qty}    = SUM(qty) by defect_type，按 qty 降序</li>
 *   <li>{@code top[i].rate}   = 该类型缺陷件数 / 总缺陷件数 × 100（<b>不良构成占比</b>）</li>
 * </ul>
 * <b>关于 {@code rate} 的分母</b>：接口文档示例给的是 {@code 0.86}（0~1 的小数），
 * 而同样字段在 byLine/byProduct 里是百分数值（如 98.4）。为了不让前端一个字段两种量纲，
 * 这里统一返回 <b>百分数值</b>（0~100），与 byLine/byProduct 保持一致，
 * 前端统一 {@code toFixed(2) + '%'} 即可。
 */
@Data
public class QualityDefectVO {

    /** 缺陷 Top 列表，按数量降序 */
    private List<Top> top;

    /** 产线维度 */
    private List<ByLine> byLine;

    /** 产品维度 */
    private List<ByProduct> byProduct;

    /** 设备维度 */
    private List<ByEquipment> byEquipment;

    /** 统计口径说明，如 "统计范围 2026-10-03 ~ 2026-11-01" */
    private String scope;

    /** 缺陷 Top 一行：某缺陷类型在窗口内的汇总 */
    @Data
    public static class Top {

        /** 缺陷类型 */
        private String type;

        /** 缺陷件数，SUM(qty) */
        private Integer qty;

        /**
         * 不良构成占比(%)：该类型缺陷件数 / 窗口内总缺陷件数 × 100。
         * <p>注意分母是「缺陷总数」不是「产量」，所以叫构成占比而不是不良率。
         */
        private BigDecimal rate;

        /** 该类型缺陷件数最多的产线 */
        private String lineName;
    }

    /** 产线维度一行 */
    @Data
    public static class ByLine {

        /** 产线名称 */
        private String lineName;

        /** 产线ID，根因分析按产线取数时用 */
        private Long lineId;

        /**
         * 良品率(%) = 该产线 Σgood / Σ(good+defect) × 100（数量口径）。
         * <p>数据源 prod_batch_record ⨝ prod_plan，按 plan_date 落在窗口内过滤。
         */
        private BigDecimal rate;

        /** 缺陷件数，SUM(prod_defect_record.qty) */
        private Integer defectQty;

        /** 该产线缺陷件数最多的类型 */
        private String mainDefect;

        /** 主要缺陷的件数 */
        private Integer mainDefectQty;

        /** 批次合格率(%) = 该产线合格批次数 / 总批次数 × 100（批次口径） */
        private BigDecimal batchRate;

        /** 较昨日良品率变化，带正负号，如 "-0.21"；昨日无数据时为 null */
        private String delta;
    }

    /** 产品维度一行 */
    @Data
    public static class ByProduct {

        /** 产品名称 */
        private String product;

        /** 该产品良品率(%) */
        private BigDecimal rate;

        /** 产量（件）= Σ(good+defect) */
        private Integer qty;

        /** 缺陷件数 */
        private Integer defectQty;
    }

    /** 设备维度一行 */
    @Data
    public static class ByEquipment {

        /** 设备名称 */
        private String equipment;

        /** 设备ID，前端「查看设备」跳转与「根因分析」取数都用它 */
        private Long equipmentId;

        /** 所属产线，对应原型「所属产线」列 */
        private String lineName;

        /** 该设备缺陷件数最多的类型 */
        private String defectType;

        /** 缺陷件数 */
        private Integer qty;

        /** 不良贡献(%) = 该设备缺陷件数 / 窗口内总缺陷件数 × 100 */
        private BigDecimal share;

        /** 设备健康度评分 0~100，来自 base_equipment.health_score */
        private Integer health;

        /** 设备风险等级：高/中/低 */
        private String riskLevel;

        /** 设备运行状态：正常/预警/故障/离线 */
        private String status;
    }
}