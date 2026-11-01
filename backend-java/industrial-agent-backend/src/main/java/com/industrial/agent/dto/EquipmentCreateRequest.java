package com.industrial.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增设备请求。
 * <p>
 * 校验分三层，缺一不可：
 * <ol>
 *   <li><b>格式</b>：{@code @NotNull/@NotBlank/@Size/@Pattern} —— 挡住明显的脏数据</li>
 *   <li><b>存在性</b>：产线 ID 是否存在 —— 只能在 Service 里查库</li>
 *   <li><b>唯一性</b>：设备编号是否重复 —— 也查库，但要排除逻辑删除的记录，因为库上有唯一键</li>
 * </ol>
 */
@Data
public class EquipmentCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备编号，唯一。格式：EQ-XX-000 */
    @NotBlank(message = "设备编号不能为空")
    @Size(max = 50, message = "设备编号长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$",
             message = "设备编号只能包含字母、数字、下划线和短横线，长度 2-50")
    private String code;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50, message = "设备名称长度不能超过 50")
    private String name;

    @NotNull(message = "所属产线不能为空")
    @Min(value = 1, message = "产线 ID 必须为正整数")
    private Long lineId;

    @Size(max = 50, message = "设备类型长度不能超过 50")
    private String type;

    @Size(max = 50, message = "型号长度不能超过 50")
    private String model;

    @Size(max = 50, message = "厂商长度不能超过 50")
    private String vendor;

    /** 健康度，默认 100 */
    @Min(value = 0, message = "健康度不能小于 0")
    @Max(value = 100, message = "健康度不能大于 100")
    private Integer health;

    /** 风险等级：高 / 中 / 低，默认「低」 */
    @Pattern(regexp = "^(高|中|低)$", message = "风险等级只能是 高 / 中 / 低")
    private String riskLevel;

    /** 设备状态，默认「正常」 */
    @Pattern(regexp = "^(正常|预警|故障|离线)$", message = "设备状态只能是 正常 / 预警 / 故障 / 离线")
    private String status;

    @Size(max = 200, message = "最近异常描述长度不能超过 200")
    private String lastException;
}