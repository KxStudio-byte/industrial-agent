package com.industrial.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改工单请求（全量覆盖，null 表示不改）。
 * <p>
 * <b>为什么状态不在这里</b>：状态推进有「只能往前、不能跳过」的业务规则，
 * 而且每次推进记录的语义不一样（开始维修 / 维修完成送检 / 验收通过）。
 * 把它塞进通用 PUT 会让「改了负责人」和「验收了工单」走同一条审计路径，
 * 事后查不出是谁把工单标成已完成的。状态走独立的
 * {@code PUT /api/work-orders/{id}/status}。
 * <p>
 * <b>设备不能改</b>：工单一旦建立就绑定设备，换设备等于换一张工单，
 * 应该删掉重建 —— 否则设备维保次数统计会错。
 */
@Data
public class WorkOrderUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 故障类型 */
    @Size(max = 50, message = "故障类型长度不能超过 50")
    private String faultType;

    /** 优先级：紧急 / 高 / 中 / 低 */
    @Pattern(regexp = "^(紧急|高|中|低)$", message = "优先级只能是 紧急 / 高 / 中 / 低")
    private String priority;

    /** 故障描述 */
    @Size(max = 1000, message = "故障描述长度不能超过 1000")
    private String description;

    /** 负责人 ID */
    @Min(value = 1, message = "负责人 ID 必须为正整数")
    private Long assigneeId;
}