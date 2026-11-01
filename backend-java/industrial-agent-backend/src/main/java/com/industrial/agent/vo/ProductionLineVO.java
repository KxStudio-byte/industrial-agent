package com.industrial.agent.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 生产总览页的产线卡片。
 * <p>
 * 对齐前端 {@code ProductionLine} 类型（{@code types/index.ts:192}）。
 * 这个 VO 字段最多，因为总览的每张产线卡片同时要展示产量、达成率、OEE 分解、
 * 损失构成和 AI 提示 —— 拆成多个接口会让前端拼装成本远大于多返回几个字段。
 * <p>
 * <b>哪些字段是实算的、哪些是占位</b>：
 * <ul>
 *   <li>实算：{@code target/actual/achieve/oee/availability/performance/qualityIndex/
 *       quality/losses/downtimeSum/workTime}；</li>
 *   <li>占位（库里没有对应列，返回 {@code null} 或空串，前端应能容忍）：
 *       {@code oeeDelta / qualityNote / achieveNote / planProgress / batchProgress /
 *       currentBatch / currentBatchQty / changeover / aiHint}。</li>
 * </ul>
 * 宁可给 null 也不编造数字 —— 编造的「较昨日 +1.2pct」上线后没人能验证真假。
 */
@Data
public class ProductionLineVO {

    /** 产线名 */
    private String name;

    private Long lineId;

    /** 状态：执行中 / 已延期 */
    private String status;

    /** 今日主产品 */
    private String product;

    /** 工作时长展示串，如 "12.0 小时" */
    private String workTime;

    /** 计划产量 */
    private Integer target;

    /** 实际产量 */
    private Integer actual;

    /** 达成率(%) */
    private BigDecimal achieve;

    /** 达成率说明 */
    private String achieveNote;

    /** OEE(%) */
    private BigDecimal oee;

    /** 较昨日 OEE 变化 */
    private String oeeDelta;

    /** 质量指数(%) */
    private BigDecimal quality;

    /** 质量说明 */
    private String qualityNote;

    /** 开动率(%) */
    private BigDecimal availability;

    /** 性能效率(%) */
    private BigDecimal performance;

    /** 质量指数(%) —— 与 {@code quality} 同值，保留双字段对齐前端类型 */
    private BigDecimal qualityIndex;

    /** 计划进度(%) */
    private BigDecimal planProgress;

    /** 批次进度(%) */
    private BigDecimal batchProgress;

    /** 当前批次名 */
    private String currentBatch;

    /** 当前批次进度描述 */
    private String currentBatchQty;

    /** 换型情况描述 */
    private String changeover;

    /** 今日累计停机分钟 */
    private Integer downtimeSum;

    /** 损失构成（类型 + 分钟） */
    private List<LossSlice> losses;

    /** AI 提示 */
    private String aiHint;

    /** 损失构成切片 */
    @Data
    public static class LossSlice {
        private String name;
        private Integer value;

        public LossSlice() {
        }

        public LossSlice(String name, Integer value) {
            this.name = name;
            this.value = value;
        }
    }
}