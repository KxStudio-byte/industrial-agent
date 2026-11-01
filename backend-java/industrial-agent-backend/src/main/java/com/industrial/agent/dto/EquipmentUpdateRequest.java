package com.industrial.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改设备请求。
 * <p>
 * <b>全部字段可选 = 局部更新语义</b>：{@code null} 表示「这个字段别动」。
 * 这样前端只提交改动过的字段即可，不会因为少传 {@code vendor} 把原有厂商清空。
 * <p>
 * 想主动清空某个字段时，传空串 {@code ""}（会归一成 null 写库）。
 */
@Data
public class EquipmentUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备编号；不传表示不改。改了要做唯一性校验 */
    @Size(max = 50, message = "设备编号长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$",
             message = "设备编号只能包含字母、数字、下划线和短横线，长度 2-50")
    private String code;

    @Size(max = 50, message = "设备名称长度不能超过 50")
    private String name;

    @Min(value = 1, message = "产线 ID 必须为正整数")
    private Long lineId;

    @Size(max = 50, message = "设备类型长度不能超过 50")
    private String type;

    @Size(max = 50, message = "型号长度不能超过 50")
    private String model;

    @Size(max = 50, message = "厂商长度不能超过 50")
    private String vendor;

    @Min(value = 0, message = "健康度不能小于 0")
    @Max(value = 100, message = "健康度不能大于 100")
    private Integer health;

    @Pattern(regexp = "^(高|中|低)$", message = "风险等级只能是 高 / 中 / 低")
    private String riskLevel;

    @Pattern(regexp = "^(正常|预警|故障|离线)$", message = "设备状态只能是 正常 / 预警 / 故障 / 离线")
    private String status;

    @Size(max = 200, message = "最近异常描述长度不能超过 200")
    private String lastException;

    /** 是否至少传了一个可修改字段（全空时直接拒绝，避免无意义的 UPDATE） */
    public boolean hasAnyField() {
        return code != null || name != null || lineId != null || type != null
                || model != null || vendor != null || health != null
                || riskLevel != null || status != null || lastException != null;
    }
}