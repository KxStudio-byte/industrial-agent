package com.industrial.agent.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户信息，放入 SecurityContext 的 principal。
 * <p>
 * 业务层取当前用户：{@code SecurityUtils.currentUser()}。
 * <p>
 * 这里除了基础信息，还带了两样东西，都是为了「无状态 JWT 也能即时失效」：
 * <ul>
 *   <li>{@link #tokenVersion}：token 里的版本号，用户改密码 / 被管理员踢下线时版本 +1，
 *       旧 token 的版本对不上就直接拒绝，不需要维护一整个黑名单</li>
 *   <li>{@link #issuedAtMillis}：token 的签发时刻（秒级），退出登录时把「<b>签发时刻 ≤ 该秒</b>」的
 *       token 全部作废</li>
 * </ul>
 */
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID（sys_user.id） */
    private final Long userId;

    /** 用户名（sys_user.username） */
    private final String username;

    /** 真实姓名（sys_user.real_name） */
    private final String realName;

    /** 角色 key（sys_role.role_key），如 ADMIN / OPERATOR */
    private final String roleKey;

    /** 角色名称（sys_role.role_name），如 系统管理员 */
    private final String roleName;

    /** 所属车间（sys_user.workshop_id） */
    private final Long workshopId;

    /** 权限点（如 USER:VIEW），由角色映射得到 */
    private final Set<String> permissions;

    /** token 版本号，用于改密码 / 踢下线后让旧 token 失效 */
    private final Integer tokenVersion;

    /** token 签发时刻（毫秒精度来自 iat*1000） */
    private final Long issuedAtMillis;

    public LoginUser(Long userId, String username, String realName, String roleKey, String roleName,
                     Long workshopId, Collection<String> permissions, Integer tokenVersion, Long issuedAtMillis) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.roleKey = roleKey;
        this.roleName = roleName;
        this.workshopId = workshopId;
        this.permissions = permissions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.tokenVersion = tokenVersion;
        this.issuedAtMillis = issuedAtMillis;
    }

    /** 兼容旧调用点（只需 id / username / roleKey 的场景） */
    public LoginUser(Long userId, String username, String roleKey) {
        this(userId, username, null, roleKey, null, null, Collections.emptySet(), null, null);
    }

    /** Spring Security 用的权限集合：角色 + 权限点 */
    public Set<String> authorityNames() {
        Set<String> names = new LinkedHashSet<>();
        if (roleKey != null && !roleKey.isBlank()) {
            names.add("ROLE_" + roleKey);
        }
        names.addAll(permissions);
        return names;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean isAdmin() {
        return PermissionConstants.ROLE_KEY_ADMIN.equals(roleKey);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public String getRoleName() {
        return roleName;
    }

    public Long getWorkshopId() {
        return workshopId;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public Long getIssuedAtMillis() {
        return issuedAtMillis;
    }

    @Override
    public String toString() {
        return "LoginUser{userId=" + userId + ", username='" + username + "', roleKey='" + roleKey
                + "', permissions=" + permissions.stream().collect(Collectors.joining(",")) + "}";
    }
}