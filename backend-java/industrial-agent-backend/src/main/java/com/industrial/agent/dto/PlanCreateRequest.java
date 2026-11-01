package com.industrial.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建生产计划请求（{@code POST /api/production/plans}）。
 * <p>
 * 字段与 {@code prod_plan} 表一一对应，只有这三个可以在新建时决定：
 * <ul>
 *   <li>{@code actualQty} —— 新建计划实际产量必为 0，由 MES 批次汇总回填；</li>
 *   <li>{@code status} —— 新建一律「待执行」，由执行过程推进；</li>
 *   <li>{@code aiExplanation} —— 由系统生成，不接受外部传入。</li>
 * </ul>
 * <b>为什么 {@code standardQtyPerHour} 必须 &gt; 0</b>：它是性能效率的分母，
 * 填 0 会让整个 OEE 计算除零，所以校验阶段直接拦掉。
 */
@Data
public class PlanCreateRequest {

    /** 计划日期 */
    @NotNull(message = "计划日期不能为空")
    private java.time.LocalDate planDate;

    /** 产线ID（base_production_line.id） */
    @NotNull(message = "产线不能为空")
    @Min(value = 1, message = "产线ID不合法")
    private Long lineId;

    /** 产品名称 */
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 100, message = "产品名称不能超过 100 个字符")
    private String productName;

    /** 计划产量 */
    @NotNull(message = "计划产量不能为空")
    @Min(value = 1, message = "计划产量必须大于 0")
    @Max(value = 1_000_000, message = "计划产量过大")
    private Integer targetQty;

    /** 计划生产工时(小时)，开动率分母 */
    @NotNull(message = "计划工时不能为空")
    @Min(value = 1, message = "计划工时至少 1 小时")
    @Max(value = 24, message = "计划工时不能超过 24 小时")
    private BigDecimal planHours;

    /** 标准节拍(件/小时)，性能效率分母，必须 > 0 */
    @NotNull(message = "标准节拍不能为空")
    @Min(value = 1, message = "标准节拍必须大于 0")
    @Max(value = 1_000_000, message = "标准节拍过大")
    private Integer standardQtyPerHour;

    /** 班次：白班/夜班 */
    @NotBlank(message = "班次不能为空")
    @Pattern(regexp = "^(白班|夜班)$", message = "班次只能是「白班」或「夜班」")
    private String shiftName;
}