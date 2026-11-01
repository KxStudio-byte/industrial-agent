package com.industrial.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工单状态推进请求。
 * <p>
 * <b>为什么不做成 {@code PUT /{id}/status?status=维修中}</b>：
 * 现场推进状态时通常要顺手写一句处理说明（「已更换 6305 轴承，试机 2 小时正常」），
 * 这句话是后续验收和复盘的关键依据。用请求体一起提交，就不会出现
 * 「状态改了但没人记得做過什么」。
 * <p>
 * 目前 {@code remark} 只做校验和日志，<b>不落库</b> —— {@code eqp_work_order} 没有备注列，
 * 要留痕得先加列或加操作流水表，那属于另一个变更，不在这里偷偷加。
 */
@Data
public class WorkOrderStatusRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标状态：待处理 / 维修中 / 待验证 / 已完成 */
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /** 处理说明（可选，仅记录日志） */
    @Size(max = 500, message = "处理说明长度不能超过 500")
    private String remark;
}