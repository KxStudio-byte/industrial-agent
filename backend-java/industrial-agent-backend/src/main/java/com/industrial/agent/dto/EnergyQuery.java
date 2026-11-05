package com.industrial.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 能耗查询参数。
 * <p>
 * 统一约定：前端传「全部」或空串视为不过滤（{@link #normalize()} 会归一成 null），
 * 与设备 / 工单 / 生产的 {@code validate + normalize} 套路保持一致。
 */
@Data
public class EnergyQuery {

    /** 电 / 水 / 气 / 蒸汽；「综合」仅用于趋势接口（折标煤口径），不做介质校验 */
    public static final List<String> VALID_MEDIUM = List.of("电", "水", "气", "蒸汽");

    /** 车间名，如 A车间 */
    public static final List<String> VALID_WORKSHOP = List.of("A车间", "B车间", "C车间");

    private static final List<String> VALID_GRANULARITY = List.of("hour", "day");

    /** 介质：电/水/气/蒸汽，null 表示全部 */
    private String medium;

    /** 车间名：A车间/B车间/C车间，null 表示全部 */
    private String workshop;

    /** 统计基准日，默认今天 */
    private LocalDate date;

    /** 统计天数（含 date 当天），默认 7 */
    @Min(1)
    @Max(365)
    private Integer days;

    /** 趋势粒度：hour 按小时 / day 按天，默认 day */
    private String granularity;

    /** 设备排行取前 N，默认 10 */
    @Min(1)
    @Max(100)
    private Integer topN;

    /** 方案查询：状态 */
    private String status;

    /** 方案查询：页码 */
    @Min(1)
    private Integer page;

    /** 方案查询：每页条数 */
    @Min(1)
    @Max(500)
    private Integer size;

    /** 关键字（匹配方案编号 / 方案名称 / 位置） */
    private String keyword;

    /**
     * 归一化：把「全部」/ 空白串统一变成 null。
     */
    public void normalize() {
        this.medium = blankToNull(this.medium);
        this.workshop = blankToNull(this.workshop);
        this.status = blankToNull(this.status);
        this.keyword = blankToNull(this.keyword);
        this.granularity = blankToNull(this.granularity);

        if (this.date == null) {
            this.date = LocalDate.now();
        }
        if (this.days == null || this.days < 1) {
            this.days = 7;
        }
        if (this.granularity == null || !VALID_GRANULARITY.contains(this.granularity)) {
            this.granularity = "day";
        }
        if (this.topN == null || this.topN < 1) {
            this.topN = 10;
        }
    }

    /** 统计起始日（含） */
    public LocalDate getFromDate() {
        LocalDate base = this.date == null ? LocalDate.now() : this.date;
        int n = (this.days == null || this.days < 1) ? 7 : this.days;
        return base.minusDays(n - 1L);
    }

    /** 统计截止日（含） */
    public LocalDate getToDate() {
        return this.date == null ? LocalDate.now() : this.date;
    }

    /** 昨日（用于环比） */
    public LocalDate getYesterday() {
        return getToDate().minusDays(1);
    }

    /** 方案分页是否生效（不传 page 时返回全量数组，与设备接口一致） */
    public boolean isPaged() {
        return this.page != null;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        if (t.isEmpty() || "全部".equals(t) || "all".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }
}