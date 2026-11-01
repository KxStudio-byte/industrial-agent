package com.industrial.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建工单请求。
 * <p>
 * 原型表单只有五个字段：设备名称、故障类型、优先级、故障描述、负责人 —— 全部必填。
 * <p>
 * <b>刻意不接收的字段</b>：
 * <ul>
 *   <li>{@code woNo} —— 编号由服务端按 {@code WO-yyyyMMdd-NNN} 生成，
 *       前端传的话两个人并发提交就可能撞号；</li>
 *   <li>{@code status} —— 新建工单一律「待处理」，调用方无权直接造一条「已完成」；</li>
 *   <li>{@code source} —— 由服务端按调用身份写入，不接受前端伪造来源。</li>
 * </ul>
 */
@Data
public class WorkOrderCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备 ID（表单里选的是「设备名称」，提交的是它对应的 id） */
    @NotNull(message = "设备不能为空")
    @Min(value = 1, message = "设备 ID 必须为正整数")
    private Long equipmentId;

    /** 故障类型，如「轴承异常」 */
    @NotBlank(message = "故障类型不能为空")
    @Size(max = 50, message = "故障类型长度不能超过 50")
    private String faultType;

    /** 优先级：紧急 / 高 / 中 / 低 */
    @NotBlank(message = "优先级不能为空")
    @Pattern(regexp = "^(紧急|高|中|低)$", message = "优先级只能是 紧急 / 高 / 中 / 低")
    private String priority;

    /** 故障描述 */
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 1000, message = "故障描述长度不能超过 1000")
    private String description;

    /** 负责人 ID（从 sys_user 里选，限定为在职人员） */
    @NotNull(message = "负责人不能为空")
    @Min(value = 1, message = "负责人 ID 必须为正整数")
    private Long assigneeId;

    /**
     * 创建来源，可选。
     * <p>
     * 人工在页面点「提交工单」时不传，服务端写「人工创建」；
     * AI Agent 自动建单时传 {@code 设备运维Agent} 之类，便于事后区分工单是谁发起的。
     * 不传不代表可以乱传 —— 服务端只认白名单里的几个前缀，见 {@code WorkOrderService}。
     */
    @Size(max = 50, message = "创建来源长度不能超过 50")
    private String source;
}