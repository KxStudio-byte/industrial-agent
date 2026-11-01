package com.industrial.agent.security;

import com.industrial.agent.common.ResultCode;
import com.industrial.agent.exception.TokenAuthenticationException;

/**
 * 认证失败的几种具体原因。
 * <p>
 * {@link TokenAuthenticationException} 本身就能带 code，但每次 new 都要写一长串枚举，
 * 过滤器里可读性太差；这里用几个静态工厂把「为什么失败」表达清楚。
 * <p>
 * 之所以保留 <b>20009 / 20008 两个不同的码</b>：前端要给用户不同提示——
 * 「账号已在其他终端登录」和「登录状态已失效」对用户来说是两件事。
 */
public final class TokenAuthErrors {

    /**
     * 「没带 token」时给用户看的完整提示。
     * <p>
     * 抽成常量是因为它出现在两条完全不同的代码路径上：过滤器判定失败时用它
     * （{@link #missing()}），而根本没带 token 时根本走不到过滤器，
     * 由 {@code JsonAuthenticationEntryPoint} 的兜底分支用。两处文案必须一致。
     */
    public static final String MISSING_MESSAGE = "未登录或登录已过期，请重新登录";

    private TokenAuthErrors() {
    }

    public static TokenAuthenticationException missing() {
        return new TokenAuthenticationException(ResultCode.UNAUTHORIZED, MISSING_MESSAGE);
    }

    /** token 合法，但库里已查不到该用户（被物理删除，或数据被清空） */
    public static TokenAuthenticationException accountNotFound() {
        return new TokenAuthenticationException(ResultCode.UNAUTHORIZED, "账号不存在或已被删除，请重新登录");
    }

    public static TokenAuthenticationException invalid() {
        return new TokenAuthenticationException(ResultCode.TOKEN_INVALID, "Token 无效，请重新登录");
    }

    public static TokenAuthenticationException expired() {
        return new TokenAuthenticationException(ResultCode.TOKEN_EXPIRED, "Token 已过期，请重新登录");
    }

    public static TokenAuthenticationException tokenRevoked() {
        return new TokenAuthenticationException(ResultCode.TOKEN_REVOKED);
    }

    public static TokenAuthenticationException sessionReplaced() {
        return new TokenAuthenticationException(ResultCode.SESSION_REPLACED);
    }

    public static TokenAuthenticationException accountDisabled() {
        return new TokenAuthenticationException(ResultCode.ACCOUNT_DISABLED, "账号已被停用，请联系管理员");
    }

    public static TokenAuthenticationException roleMissing() {
        return new TokenAuthenticationException(ResultCode.FORBIDDEN, "账号未分配角色，请联系管理员");
    }

    public static TokenAuthenticationException roleDisabled() {
        return new TokenAuthenticationException(ResultCode.ROLE_DISABLED, "角色已停用，请联系管理员");
    }
}