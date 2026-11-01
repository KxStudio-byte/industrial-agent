package com.industrial.agent.security;

/**
 * 权限字符串常量（配合 {@code @PreAuthorize("hasAuthority('xxx')")} 使用）。
 * <p>
 * <b>为什么要有这一层</b>：{@code sys_role_menu} 里存的是菜单权限，粒度太粗（一个菜单里既有查询也有删除），
 * 而「操作员能不能停用账号」这种判断必须落到<b>操作</b>上。这里的常量就是操作级权限点，
 * 角色到权限点的映射只在 {@link PermissionRules} 一处维护，别的地方不要再写死角色名。
 * <p>
 * 命名规范：{@code 模块:操作}，全大写下划线。
 */
public final class PermissionConstants {

    private PermissionConstants() {
    }

    /** 查看用户列表 */
    public static final String USER_VIEW = "USER:VIEW";
    /** 重置他人密码（管理员专属） */
    public static final String USER_RESET_PASSWORD = "USER:RESET_PASSWORD";
    /** 启用 / 停用账号（管理员专属） */
    public static final String USER_CHANGE_STATUS = "USER:CHANGE_STATUS";

    /** 查看角色 */
    public static final String ROLE_VIEW = "ROLE:VIEW";

    /* ---------- 设备中心 ---------- */

    /** 查看设备（列表 / 详情 / 状态 / 运行数据 / 总览） */
    public static final String EQUIPMENT_VIEW = "EQUIPMENT:VIEW";
    /** 新增设备 */
    public static final String EQUIPMENT_CREATE = "EQUIPMENT:CREATE";
    /** 修改设备 */
    public static final String EQUIPMENT_UPDATE = "EQUIPMENT:UPDATE";
    /** 删除设备 */
    public static final String EQUIPMENT_DELETE = "EQUIPMENT:DELETE";
    /** 上报设备运行数据（采集网关 / 操作员） */
    public static final String EQUIPMENT_REPORT_RUNTIME = "EQUIPMENT:REPORT_RUNTIME";

    /* ---------- 维修工单 ---------- */

    /** 查看工单（列表 / 详情 / 统计） */
    public static final String WORK_ORDER_VIEW = "WORK_ORDER:VIEW";
    /** 新建工单 */
    public static final String WORK_ORDER_CREATE = "WORK_ORDER:CREATE";
    /** 修改工单（改优先级 / 描述 / 负责人） */
    public static final String WORK_ORDER_UPDATE = "WORK_ORDER:UPDATE";
    /** 推进工单状态（待处理 → 维修中 → 待验证 → 已完成） */
    public static final String WORK_ORDER_TRANSITION = "WORK_ORDER:TRANSITION";
    /** 删除工单（管理员专属，逻辑删除） */
    public static final String WORK_ORDER_DELETE = "WORK_ORDER:DELETE";

    /* ---------- 生产运营 ---------- */

    /** 查看生产运营数据（生产总览 / 生产计划 / OEE 分析） */
    public static final String PRODUCTION_VIEW = "PRODUCTION:VIEW";

    /** 新建/维护生产计划 */
    public static final String PRODUCTION_EDIT = "PRODUCTION:EDIT";

    /* ---------- 质量中心 ---------- */

    /** 查看质量数据（质量总览 / 缺陷统计 / 根因分析） */
    public static final String QUALITY_VIEW = "QUALITY:VIEW";

    /** 查看认证演示资源 */
    public static final String AUTH_DEMO_VIEW = "AUTH:DEMO_VIEW";
    /** 访问管理员专属演示资源 */
    public static final String AUTH_DEMO_ADMIN = "AUTH:DEMO_ADMIN";

    /** 管理员角色 key */
    public static final String ROLE_KEY_ADMIN = "ADMIN";
    /** 车间主管角色 key */
    public static final String ROLE_KEY_MANAGER = "MANAGER";
    /** 操作员角色 key */
    public static final String ROLE_KEY_OPERATOR = "OPERATOR";
}