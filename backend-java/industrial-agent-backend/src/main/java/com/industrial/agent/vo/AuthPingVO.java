package com.industrial.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 认证接口自检结果（{@code GET /api/auth/ping}，任何已登录角色都能访问）。
 * <p>
 * 用来让前端/联调方一眼看清「我这个 token 现在是谁、有什么权限、什么时候过期」，
 * 不用去解 JWT 或者抓登录响应。
 */
@Data
@Builder
public class AuthPingVO implements Serializable {

    private Long userId;

    private String username;

    private String realName;

    private String roleKey;

    private String roleName;

    /** 权限点，逗号拼接便于肉眼核对 */
    private String permissions;

    /** 签发时刻（毫秒）。原生 long = JSON 数字，前端可直接做时间差计算 */
    private long issuedAt;

    /** 过期时刻（毫秒）。原生 long = JSON 数字 */
    private long expiresAt;

    /** 剩余有效秒数 */
    private long remainingSeconds;
}