package com.industrial.agent.controller;

import com.industrial.agent.common.PageResult;
import com.industrial.agent.common.Result;
import com.industrial.agent.dto.WorkOrderCreateRequest;
import com.industrial.agent.dto.WorkOrderQuery;
import com.industrial.agent.dto.WorkOrderStatusRequest;
import com.industrial.agent.dto.WorkOrderUpdateRequest;
import com.industrial.agent.security.PermissionConstants;
import com.industrial.agent.service.WorkOrderService;
import com.industrial.agent.vo.WorkOrderDetailVO;
import com.industrial.agent.vo.WorkOrderRowVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 维修工单接口。
 * <pre>
 * 1. 列表   GET    /api/work-orders              查询参数见 WorkOrderQuery
 * 2. 详情   GET    /api/work-orders/{id}         工单 + 关联设备快照，一次返回
 * 3. 新增   POST   /api/work-orders              返回真实写入的工单（含系统生成的 woNo）
 * 4. 修改   PUT    /api/work-orders/{id}         不含状态、不含设备
 * 5. 改状态 PUT    /api/work-orders/{id}/status  单向推进，带流转校验
 * 6. 删除   DELETE /api/work-orders/{id}         逻辑删除，管理员专属
 * 7. 统计   GET    /api/work-orders/summary      工单页 4 张 KPI 卡
 * 8. 选项   GET    /api/work-orders/options      下拉框数据源
 * </pre>
 * <p>
 * <b>路径顺序</b>：{@code /summary}、{@code /options} 这类字面量必须能匹配到。
 * Spring 的 {@code AntPathMatcher} 对字面量优先于路径变量，
 * 且 {@code id} 是 {@code Long}，传 "summary" 会在类型转换阶段 400 ——
 * 所以本类把字面量路径写在 {@code /{id}} <b>之前</b>，靠顺序 + 具体度双重保证。
 * <p>
 * <b>为什么单独开一个 /api/work-orders 而不是挂在 /api/equipment 下</b>：
 * 工单是独立业务实体，一台设备可以有多张工单，用 {@code /api/equipment/{id}/work-orders}
 * 会让人以为工单是设备的附属资源；而且「我的工单」「按产线看」这些查询跟某台设备无关。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    /* ============================================================
     * 1. 工单列表（分页 + 条件筛选）
     * ============================================================ */

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_VIEW + "')")
    public Result<Object> list(@Valid WorkOrderQuery query) {
        Object data = workOrderService.list(query);
        String msg = data instanceof PageResult<?> p
                ? String.format("共 %d 条，第 %d/%d 页", p.getTotal(), p.getPage(), Math.max(p.getPages(), 1))
                : "共 " + ((List<?>) data).size() + " 条";
        return Result.success(data, msg);
    }

    /* ============================================================
     * 2. 工单详情（含关联设备）
     * ============================================================ */

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_VIEW + "')")
    public Result<WorkOrderDetailVO> detail(
            @PathVariable("id") @Min(value = 1, message = "工单 ID 必须为正整数") Long id) {
        return Result.success(workOrderService.detail(id));
    }

    /* ============================================================
     * 3. 新建工单
     * ============================================================ */

    /**
     * 新建工单。
     * <p>
     * 返回的是<b>写库后重新查出来的整行</b>，不是请求体回显 —— 前端拿到就能直接插进列表，
     * 不用再刷一次列表才能看到工单号。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_CREATE + "')")
    public Result<WorkOrderRowVO> create(@RequestBody @Valid WorkOrderCreateRequest request) {
        WorkOrderRowVO vo = workOrderService.create(request);
        log.info("新建工单成功 woNo={} equipmentId={}", vo.getWoNo(), vo.getEquipmentId());
        return Result.success(vo, "工单创建成功，工单号 " + vo.getWoNo());
    }

    /* ============================================================
     * 4. 修改工单
     * ============================================================ */

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_UPDATE + "')")
    public Result<WorkOrderRowVO> update(
            @PathVariable("id") @Min(value = 1, message = "工单 ID 必须为正整数") Long id,
            @RequestBody @Valid WorkOrderUpdateRequest request) {
        return Result.success(workOrderService.update(id, request), "工单修改成功");
    }

    /* ============================================================
     * 5. 状态推进
     * ============================================================ */

    /**
     * 推进工单状态（待处理 → 维修中 → 待验证 → 已完成）。
     * <p>
     * 单独一个接口而不是放进 PUT：状态变更是有业务规则的动作，和「改个优先级」不是同一类事。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_TRANSITION + "')")
    public Result<WorkOrderRowVO> changeStatus(
            @PathVariable("id") @Min(value = 1, message = "工单 ID 必须为正整数") Long id,
            @RequestBody @Valid WorkOrderStatusRequest request) {
        WorkOrderRowVO vo = workOrderService.transition(id, request);
        return Result.success(vo, "工单状态已变更为「" + vo.getStatus() + "」");
    }

    /* ============================================================
     * 6. 删除工单（逻辑删除）
     * ============================================================ */

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_DELETE + "')")
    public Result<Void> delete(
            @PathVariable("id") @Min(value = 1, message = "工单 ID 必须为正整数") Long id) {
        workOrderService.delete(id);
        return Result.success(null, "工单删除成功");
    }

    /* ============================================================
     * 7. 统计
     * ============================================================ */

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_VIEW + "')")
    public Result<Map<String, Object>> summary() {
        return Result.success(workOrderService.summary());
    }

    /* ============================================================
     * 8. 下拉选项
     * ============================================================ */

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WORK_ORDER_VIEW + "')")
    public Result<Map<String, Object>> options() {
        return Result.success(workOrderService.options());
    }
}