package com.industrial.agent.dto;

import com.industrial.agent.common.ResultCode;
import com.industrial.agent.exception.BusinessException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单列表查询条件（全部可选）。
 * <p>
 * 与 {@link EquipmentQuery} 保持同一套约定：{@code 全部} / 空串一律归一成 null，
 * 枚举值做白名单校验（传了非法值直接 400，而不是安静返回空列表）。
 */
@Data
public class WorkOrderQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 合法状态，与 eqp_work_order.status 的注释保持一致 */
    public static final List<String> VALID_STATUS = List.of("待处理", "维修中", "待验证", "已完成");

    /** 合法优先级 */
    public static final List<String> VALID_PRIORITY = List.of("紧急", "高", "中", "低");

    /** 合法排序字段，与 WorkOrderMapper 里的 &lt;choose&gt; 分支保持一致 */
    private static final List<String> VALID_SORT_FIELD = List.of("id", "createTime", "priority", "status");

    /** 工单状态：待处理 / 维修中 / 待验证 / 已完成；「全部」或空表示不过滤 */
    private String status;

    /** 优先级：紧急 / 高 / 中 / 低；「全部」或空表示不过滤 */
    private String priority;

    /** 故障类型，精确匹配（下拉选项来自 /options 的 faultTypes） */
    private String faultType;

    /** 设备 ID：查某台设备的维修历史 */
    private Long equipmentId;

    /** 负责人 ID：查「我的工单」 */
    private Long assigneeId;

    /** 产线名称，如 A产线 */
    private String lineName;

    /** 创建来源，如 人工创建 / 设备运维Agent */
    private String source;

    /** 创建时间下限（含） */
    private LocalDateTime startTime;

    /** 创建时间上限（含） */
    private LocalDateTime endTime;

    /** 关键词：匹配工单编号、设备名称、设备编号 */
    private String keyword;

    /**
     * 页码，从 1 开始。不传 = 不分页（返回数组），传了 = 返回 PageResult。
     * 与设备列表保持一致的双模式。
     */
    @Min(value = 1, message = "页码最小为 1")
    private Long page;

    /** 每页条数，最大 500 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 500, message = "每页条数最大为 500")
    private Long size;

    /** 排序字段：id / createTime / priority / status（默认 createTime） */
    private String sortField;

    /** 排序方向：asc / desc（默认 desc，新工单排最前） */
    private String sortOrder;

    /** 把 UI 上的「全部」、空串、空白统一归一成 null */
    public void normalize() {
        this.status = blankToNull(this.status);
        this.priority = blankToNull(this.priority);
        this.faultType = blankToNull(this.faultType);
        this.lineName = blankToNull(this.lineName);
        this.source = blankToNull(this.source);
        this.keyword = blankToNull(this.keyword);
        this.sortField = blankToNull(this.sortField);
        this.sortOrder = blankToNull(this.sortOrder);
        if (this.size == null) {
            this.size = 10L;
        }
    }

    /** 枚举字段白名单校验（在 {@link #normalize()} 之后调用） */
    public void validate() {
        if (status != null && !VALID_STATUS.contains(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "status 只能是 " + String.join("/", VALID_STATUS) + "，当前值：" + status);
        }
        if (priority != null && !VALID_PRIORITY.contains(priority)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "priority 只能是 " + String.join("/", VALID_PRIORITY) + "，当前值：" + priority);
        }
        if (sortField != null && !VALID_SORT_FIELD.contains(sortField)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "sortField 只能是 " + String.join("/", VALID_SORT_FIELD) + "，当前值：" + sortField);
        }
        if (sortOrder != null && !"asc".equals(sortOrder) && !"desc".equals(sortOrder)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "sortOrder 只能是 asc/desc，当前值：" + sortOrder);
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "时间区间不合法：startTime 晚于 endTime");
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