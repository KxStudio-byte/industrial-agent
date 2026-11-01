package com.industrial.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质量统计查询参数。
 *
 * <p>所有字段都可空，为空时由 {@link #resolveDefaults()} 补齐：
 * <ul>
 *   <li>{@code date} 为空 → 取今天</li>
 *   <li>{@code days} 为空 → 取 30（缺陷 Top / 分维度统计的周期）</li>
 * </ul>
 *
 * <p><b>周期口径（必须区分，不能混用）：</b>
 * <ul>
 *   <li>KPI 的「今日缺陷数」「良品率」→ 按 {@code date} 单日</li>
 *   <li>KPI 的「客诉」→ 按 {date 所在周}，周一到周日</li>
 *   <li>缺陷 Top / 分维度统计 → 按 {@code days} 近 N 天</li>
 * </ul>
 */
@Data
public class QualityQuery {

    /** 统计基准日期，默认今天。格式 yyyy-MM-dd */
    private LocalDate date;

    /** 缺陷统计窗口天数（缺陷 Top / 分维度统计用），默认 30，范围 1~365 */
    @Min(value = 1, message = "days 最小 1")
    @Max(value = 365, message = "days 最大 365")
    private Integer days;

    /**
     * 批次合格判定阈值(%)：批次不良率 ≤ 该值即判定为「合格批次」。
     *
     * <p><b>为什么需要阈值而不是「不良=0 才算合格」：</b>
     * 实测库内 360 条批次的 {@code defect_qty} 全部大于 0（范围 19~55 件/批，
     * 不良率约 2.5%~3.3%），若按零缺陷判定，批次合格率恒为 0%，指标失去意义。
     * 注塑/冲压行业通行的批次允收口径就是「不良率不超过允收线」。
     *
     * <p><b>默认 4.0 的依据（按当前真实数据标定）：</b>
     * <ul>
     *   <li>1% → 0% 批次合格（无数据符合）</li>
     *   <li>2% → 2.2%</li>
     *   <li>3% → 31.1%（会把大多数正常批次判为不合格，过于严苛）</li>
     *   <li><b>4% → 88.9%</b>（绝大多数正常批次合格，异常批次能筛出来）</li>
     *   <li>5% → 100%（无区分度）</li>
     * </ul>
     *
     * <p>范围 0~50：0 等价于「零缺陷才算合格」，50 已经覆盖到任何合理的允收线。
     */
    @Min(value = 0, message = "批次合格阈值最小 0")
    @Max(value = 50, message = "批次合格阈值最大 50")
    private BigDecimal batchPassThreshold;

    /** 补齐默认值，返回 {@code this} 便于链式调用 */
    public QualityQuery resolveDefaults() {
        if (this.date == null) {
            this.date = LocalDate.now();
        }
        if (this.days == null) {
            this.days = 30;
        }
        if (this.batchPassThreshold == null) {
            this.batchPassThreshold = DEFAULT_BATCH_PASS_THRESHOLD;
        }
        return this;
    }

    /** 批次合格默认阈值(%) */
    public static final BigDecimal DEFAULT_BATCH_PASS_THRESHOLD = new BigDecimal("4.0");

    /** 统计窗口结束日（含），即 date */
    public LocalDate getToDate() {
        return date;
    }

    /** 统计窗口起始日（含），即 date - (days - 1) */
    public LocalDate getFromDate() {
        return date.minusDays(days - 1L);
    }

    /** 昨日，用于「较昨日」 */
    public LocalDate getYesterday() {
        return date.minusDays(1);
    }

    /**
     * 当前周周一。
     * <p>{@link LocalDate#getDayOfWeek()} 的 Monday=1 … Sunday=7，直接减即可。
     */
    public LocalDate getWeekStart() {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    /** 当前周周日 */
    public LocalDate getWeekEnd() {
        return getWeekStart().plusDays(6);
    }
}