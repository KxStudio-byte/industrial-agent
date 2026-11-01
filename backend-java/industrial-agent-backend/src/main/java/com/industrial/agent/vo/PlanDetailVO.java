package com.industrial.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 生产计划详情（一次返回计划主体 + 批次 + 损失，前端一个抽屉搞定）。
 * <p>
 * <b>为什么不拆成三个接口</b>：抽屉打开时三块 UI 同时要渲染，拆开就是三次往返、
 * 三个 loading 态，还可能出现批次刷出来了损失还在转的中间态。
 */
@Data
public class PlanDetailVO {

    /** 计划主体（复用列表行 VO，含产线名与达成率） */
    private PlanRowVO plan;

    /** 批次明细 */
    private List<BatchVO> batches;

    /** 该计划下全部损失明细（跨批次汇总） */
    private List<OeeLossVO> losses;
}