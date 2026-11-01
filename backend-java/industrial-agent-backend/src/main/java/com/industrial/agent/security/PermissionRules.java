package com.industrial.agent.security;

import com.industrial.agent.common.ResultCode;
import com.industrial.agent.entity.SysRole;
import com.industrial.agent.entity.SysUser;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 角色 → 权限点映射。
 * <p>
 * <b>为什么集中放一处</b>：授权规则一旦分散到各个 Controller 的注解里就没人能说清
 * 「操作员到底能干什么」。这里用一张表把三种角色的权限写死，
 * Controller 上只写 {@code @PreAuthorize("hasAuthority('USER:VIEW')")}，
 * 想调整权限只改这一个文件。
 * <p>
 * 生产环境要做「角色可配置」时，把这张表换成查 {@code sys_menu.perms}（按角色关联）即可，
 * 上层注解一行都不用动。
 */
public final class PermissionRules {

    private PermissionRules() {
    }

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            // 系统管理员：全部权限
            PermissionConstants.ROLE_KEY_ADMIN, Set.of(
                    PermissionConstants.USER_VIEW,
                    PermissionConstants.USER_RESET_PASSWORD,
                    PermissionConstants.USER_CHANGE_STATUS,
                    PermissionConstants.ROLE_VIEW,
                    PermissionConstants.AUTH_DEMO_VIEW,
                    PermissionConstants.AUTH_DEMO_ADMIN,
                    PermissionConstants.EQUIPMENT_VIEW,
                    PermissionConstants.EQUIPMENT_CREATE,
                    PermissionConstants.EQUIPMENT_UPDATE,
                    PermissionConstants.EQUIPMENT_DELETE,
                    PermissionConstants.EQUIPMENT_REPORT_RUNTIME,
                    PermissionConstants.WORK_ORDER_VIEW,
                    PermissionConstants.WORK_ORDER_CREATE,
                    PermissionConstants.WORK_ORDER_UPDATE,
                    PermissionConstants.WORK_ORDER_TRANSITION,
                    PermissionConstants.WORK_ORDER_DELETE,
                    PermissionConstants.PRODUCTION_VIEW,
                    PermissionConstants.PRODUCTION_EDIT,
                    PermissionConstants.QUALITY_VIEW
            ),
            // 车间主管：只读 + 查看角色，不能管用户；设备可改不可增删
            PermissionConstants.ROLE_KEY_MANAGER, Set.of(
                    PermissionConstants.USER_VIEW,
                    PermissionConstants.ROLE_VIEW,
                    PermissionConstants.AUTH_DEMO_VIEW,
                    PermissionConstants.EQUIPMENT_VIEW,
                    PermissionConstants.EQUIPMENT_UPDATE,
                    PermissionConstants.WORK_ORDER_VIEW,
                    PermissionConstants.WORK_ORDER_CREATE,
                    PermissionConstants.WORK_ORDER_UPDATE,
                    PermissionConstants.WORK_ORDER_TRANSITION,
                    PermissionConstants.PRODUCTION_VIEW,
                    PermissionConstants.PRODUCTION_EDIT,
                    PermissionConstants.QUALITY_VIEW
            ),
            // 操作员：现场只能看设备 + 上报运行数据，改不了台账
            // 工单是现场最贴近的一环：能看、能报、能推进状态，但不能删、不能改别人的派工单
            // 生产运营也是只读：现场看得懂自己的产量和 OEE，但不该能改计划
            // 质量同样只读：现场要看良品率与缺陷分布，但归因结论不由现场改
            PermissionConstants.ROLE_KEY_OPERATOR, Set.of(
                    PermissionConstants.AUTH_DEMO_VIEW,
                    PermissionConstants.EQUIPMENT_VIEW,
                    PermissionConstants.EQUIPMENT_REPORT_RUNTIME,
                    PermissionConstants.WORK_ORDER_VIEW,
                    PermissionConstants.WORK_ORDER_CREATE,
                    PermissionConstants.WORK_ORDER_TRANSITION,
                    PermissionConstants.PRODUCTION_VIEW,
                    PermissionConstants.QUALITY_VIEW
            )
    );

    /** 取角色的权限点；未知角色返回空集合（宁可不给，也不要给多） */
    public static Set<String> permissionsOf(String roleKey) {
        if (roleKey == null) {
            return Collections.emptySet();
        }
        return ROLE_PERMISSIONS.getOrDefault(roleKey, Collections.emptySet());
    }

    /**
     * 取某个角色实际生效的权限点。
     * <p>
     * <b>优先级</b>：{@code sys_role.perms} 有值 → 用它；为空 → 回落到上面的内置默认映射。
     * 这样默认安装即可用（不用先灌权限数据），需要调整时改库即可生效，
     * 不用为了加一个权限点重新发版。
     *
     * @param role 已查库的角色实体，可为 null
     */
    public static Set<String> permissionsOf(SysRole role) {
        if (role == null) {
            return Collections.emptySet();
        }
        Set<String> fromDb = parsePerms(role.getPerms());
        return fromDb.isEmpty() ? permissionsOf(role.getRoleKey()) : fromDb;
    }

    /**
     * 解析 {@code sys_role.perms} 字段。
     * <p>
     * 同时兼容英文逗号、中文逗号、分号与空白分隔 —— 运维手工填这个字段时几乎一定会混用。
     * 不带 {@code 模块:操作} 形式的脏数据直接丢弃，不做猜测。
     */
    public static Set<String> parsePerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split("[,，;；\\s]+")) {
            String p = token.trim();
            if (!p.isEmpty() && p.indexOf(':') > 0 && p.indexOf(':') < p.length() - 1) {
                result.add(p.toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    /**
     * 由数据库用户 + 角色组装 {@link LoginUser}。
     *
     * @param tokenVersion  token 版本号（来自库里的 sys_user.version）
     * @param issuedAtMillis token 签发时刻，登录时传当前时间
     */
    public static LoginUser buildLoginUser(SysUser user, SysRole role, Integer tokenVersion, long issuedAtMillis) {
        String roleKey = role == null ? PermissionConstants.ROLE_KEY_OPERATOR : role.getRoleKey();
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                roleKey,
                role == null ? null : role.getRoleName(),
                user.getWorkshopId(),
                new LinkedHashSet<>(permissionsOf(role)),
                tokenVersion,
                issuedAtMillis
        );
    }

    /** 构建 admin 之外的默认权限集合（供单元测试/调试使用） */
    public static Set<String> allPermissions() {
        Set<String> all = new LinkedHashSet<>();
        ROLE_PERMISSIONS.values().forEach(all::addAll);
        return all;
    }

    /** 角色被停用时返回的错误码 */
    public static ResultCode roleDisabledCode() {
        return ResultCode.ROLE_DISABLED;
    }
}