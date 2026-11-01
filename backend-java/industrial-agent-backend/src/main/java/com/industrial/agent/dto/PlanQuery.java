package com.industrial.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 生产计划查询参数（GET /api/production/plans）。
 * <p>
 * 与 {@code EquipmentQuery} / {@code WorkOrderQuery} 保持同一套约定：
 * <ul>
 *   <li>不传 {@code page}/{@code size} 返回数组，传了返回分页对象；</li>
 *   <li>空串和 "全部" 统一归一成 null，由 {@link #normalize()} 处理；</li>
 *   <li>非法值由 {@link #validate()} 抛 400，而不是静默忽略。</li>
 * </ul>
 */
@Data
public class PlanQuery {

    /** 计划状态白名单 */
    public static final List<String> VALID_STATUS = List.of("待执行", "执行中", "已延期", "已完成");

    /** 班次白名单 */
    public static final List<String> VALID_SHIFT = List.of("白班", "夜班");

    /** 排序字段白名单（白名单是为了防 SQL 注入：ORDER BY 不能用 #{} 参数化） */
    private static final List<String> VALID_SORT_FIELD =
            List.of("id", "planDate", "targetQty", "actualQty", "achieve", "status");

    /** 状态：待执行/执行中/已延期/已完成 */
    private String status;

    /** 产线ID */
    private Long lineId;

    /** 产线名（A产线/B产线/C产线），与 lineId 二选一 */
    private String lineName;

    /** 班次：白班/夜班 */
    private String shiftName;

    /** 产品名模糊匹配 */
    private String product;

    /** 关键词：匹配产品名或 AI 说明 */
    private String keyword;

    /** 计划日期起（含） */
    private LocalDate startDate;

    /** 计划日期止（含） */
    private LocalDate endDate;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 500, message = "每页条数最大为 500")
    private Integer size;

    /** 排序字段，默认 planDate */
    private String sortField;

    /** 排序方向：asc/desc，默认 desc */
    @Pattern(regexp = "(?i)^(asc|desc)$", message = "排序方向只能是 asc 或 desc")
    private String sortOrder;

    /** 把空串、"全部"、"all" 归一成 null，避免 SQL 里被当成真实条件 */
    public void normalize() {
        status = blankToNull(status);
        lineName = blankToNull(lineName);
        shiftName = blankToNull(shiftName);
        product = blankToNull(product);
        keyword = blankToNull(keyword);
        sortField = blankToNull(sortField);
        sortOrder = blankToNull(sortOrder);
        if (sortOrder != null) {
            sortOrder = sortOrder.toLowerCase();
        }
    }

    /** 参数合法性校验，不合法抛 400 */
    public void validate() {
        if (status != null && !VALID_STATUS.contains(status)) {
            throw new com.industrial.agent.exception.BusinessException(
                    com.industrial.agent.common.ResultCode.PARAM_ERROR,
                    "状态只能是 " + String.join("/", VALID_STATUS) + "，当前值：" + status);
        }
        if (shiftName != null && !VALID_SHIFT.contains(shiftName)) {
            throw new com.industrial.agent.exception.BusinessException(
                    com.industrial.agent.common.ResultCode.PARAM_ERROR,
                    "班次只能是 " + String.join("/", VALID_SHIFT) + "，当前值：" + shiftName);
        }
        if (sortField != null && !VALID_SORT_FIELD.contains(sortField)) {
            throw new com.industrial.agent.exception.BusinessException(
                    com.industrial.agent.common.ResultCode.PARAM_ERROR,
                    "排序字段只能是 " + String.join("/", VALID_SORT_FIELD) + "，当前值：" + sortField);
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new com.industrial.agent.exception.BusinessException(
                    com.industrial.agent.common.ResultCode.PARAM_ERROR,
                    "开始日期不能晚于结束日期");
        }
    }

    /** 是否为分页查询 */
    public boolean isPaged() {
        return page != null && size != null;
    }

    /** 实际排序字段（兜底 planDate） */
    public String effectiveSortField() {
        return sortField == null ? "planDate" : sortField;
    }

    /** 实际排序方向（兜底 desc） */
    public String effectiveSortOrder() {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
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