package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 节能优化方案（列表 / 抽屉详情共用）。
 * <p>
 * {@code saving} 为<b>每月</b>节约金额；{@code dailySaving} 是 saving/30 的日折算，
 * 供总览页「每日可节约」用，两者都给出避免前端自己算导致口径不一致。
 */
@Data
@Builder
public class EnergySavingPlanVO {

    private Long id;
    private String planCode;
    private String planName;
    private String location;
    private String medium;
    /** 待执行 / 已完成 / 执行失败 */
    private String status;
    /** 每月节约金额（元） */
    private BigDecimal saving;
    /** 每日节约金额（元），= saving / 30 保留 2 位 */
    private BigDecimal dailySaving;
    private String reason;
    private String advice;
    private String scope;
    private String result;
    private LocalDateTime execTime;
    private String source;
    private LocalDateTime createTime;

    /** 列表包装（带状态计数） */
    @Data
    @Builder
    public static class PlanListVO {
        private List<EnergySavingPlanVO> list;
        /** 总条数（分页后为总数，不分页为 list.size()） */
        private long total;
        /** 待执行数（全表统计，不受分页影响） */
        private long pendingCount;
        /** 已完成数 */
        private long doneCount;
        /** 执行失败数 */
        private long failedCount;
        /** 每月合计节约（元），仅统计待执行方案 */
        private BigDecimal pendingSaving;
    }
}