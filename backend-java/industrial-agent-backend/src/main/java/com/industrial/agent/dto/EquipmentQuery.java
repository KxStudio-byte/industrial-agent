package com.industrial.agent.dto;

import com.industrial.agent.common.ResultCode;
import com.industrial.agent.exception.BusinessException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备列表查询条件（全部可选）。
 * <p>
 * <b>为什么用 `全部` 而不是 null 表示「不过滤」</b>：前端的状态下拉框第一项就是字面量「全部」，
 * 直接把 UI 的值透传过来最省事。这里在 {@code normalize()} 里统一把它和空串都归一成 null，
 * Controller 和 Service 都不用再判断一遍。
 */
@Data
public class EquipmentQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 合法的设备状态 */
    private static final List<String> VALID_STATUS = List.of("正常", "预警", "故障", "离线");

    /** 合法的风险等级 */
    private static final List<String> VALID_RISK = List.of("高", "中", "低");

    /** 合法的排序字段，与 BaseEquipmentMapper 里的 {@code <choose>} 分支保持一致 */
    private static final List<String> VALID_SORT_FIELD = List.of("id", "health", "code", "updateTime");

    /** 设备状态：正常 / 预警 / 故障 / 离线；「全部」或空表示不过滤 */
    private String status;

    /** 风险等级：高 / 中 / 低；「全部」或空表示不过滤 */
    private String riskLevel;

    /** 产线名称，如 A产线 */
    private String lineName;

    /** 产线 ID（与 lineName 二选一，同时传时 lineId 优先） */
    private Long lineId;

    /** 设备类型：注塑 / 冲压 / 装配 … */
    private String equipmentType;

    /** 关键词：匹配设备名称、编号、厂商 */
    private String keyword;

    /** 健康度区间下限 */
    @Min(value = 0, message = "健康度下限不能小于 0")
    @Max(value = 100, message = "健康度下限不能大于 100")
    private Integer minHealth;

    /** 健康度区间上限 */
    @Min(value = 0, message = "健康度上限不能小于 0")
    @Max(value = 100, message = "健康度上限不能大于 100")
    private Integer maxHealth;

    /**
     * 页码，从 1 开始。
     * <p>
     * <b>不传 = 不分页</b>（返回数组），传了 = 返回分页对象。
     * 这是接口清单里约定的双模式，为的是兼容前端「全量取数 + 客户端分页」的老逻辑。
     */
    @Min(value = 1, message = "页码最小为 1")
    private Long page;

    /** 每页条数，最大 500（与 MyBatis-Plus 的 maxLimit 一致） */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 500, message = "每页条数最大为 500")
    private Long size;

    /** 排序字段：health / code / updateTime / id（默认 id） */
    private String sortField;

    /** 排序方向：asc / desc（默认 desc） */
    private String sortOrder;

    /** 把 UI 上的「全部」、空串、空白统一归一成 null */
    public void normalize() {
        this.status = blankToNull(this.status);
        this.riskLevel = blankToNull(this.riskLevel);
        this.lineName = blankToNull(this.lineName);
        this.equipmentType = blankToNull(this.equipmentType);
        this.keyword = this.keyword == null ? null : this.keyword.trim();
        this.keyword = blankToNull(this.keyword);
        this.sortField = blankToNull(this.sortField);
        this.sortOrder = blankToNull(this.sortOrder);
        if (this.size == null) {
            this.size = 10L;
        }
    }

    /**
     * 枚举字段白名单校验。
     * <p>
     * <b>为什么必须做</b>：这些字段走的是 {@code WHERE status = #{status}}，
     * 传了非法值（比如 {@code status=xxx}）SQL 本身不会报错，只会安静地返回 0 条。
     * 前端拿到空列表会以为是「确实没有这种状态的设备」，排查时很难想到是参数拼错了。
     * 这里直接拒掉，让调用方第一次就看见错误。
     * <p>
     * 在 {@link #normalize()} 之后调用，此时 {@code 全部}/空串已归一成 null，不会被误判。
     */
    public void validate() {
        if (status != null && !VALID_STATUS.contains(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "status 只能是 " + String.join("/", VALID_STATUS) + "，当前值：" + status);
        }
        if (riskLevel != null && !VALID_RISK.contains(riskLevel)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "riskLevel 只能是 " + String.join("/", VALID_RISK) + "，当前值：" + riskLevel);
        }
        if (sortField != null && !VALID_SORT_FIELD.contains(sortField)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "sortField 只能是 " + String.join("/", VALID_SORT_FIELD) + "，当前值：" + sortField);
        }
        if (sortOrder != null && !"asc".equals(sortOrder) && !"desc".equals(sortOrder)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "sortOrder 只能是 asc/desc，当前值：" + sortOrder);
        }
        if (minHealth != null && maxHealth != null && minHealth > maxHealth) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "健康度区间不合法：minHealth=" + minHealth + " 大于 maxHealth=" + maxHealth);
        }
    }

    /** 是否启用分页 */
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