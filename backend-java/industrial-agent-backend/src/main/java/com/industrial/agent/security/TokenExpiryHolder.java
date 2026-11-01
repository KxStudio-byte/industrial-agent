package com.industrial.agent.security;

/**
 * 当前请求的 token 过期时刻持有者（ThreadLocal）。
 * <p>
 * {@link JwtAuthenticationFilter} 解析 token 时把 {@code exp} 写到当前线程，
 * {@code AuthController} 读取后返回给前端做「登录有效期倒计时 / 自动登出」。
 * 请求结束由过滤器 remove，避免线程池复用导致的脏数据串台。
 * <p>
 * 为什么不塞进 {@link LoginUser}：过期时间属于协议层，不属于业务身份，
 * 混进 principal 会让「序列化登录用户」这类操作意外带上一个随每次登录变化的时间戳。
 */
public final class TokenExpiryHolder {

    private static final ThreadLocal<Long> EXPIRES_AT = new ThreadLocal<>();

    private TokenExpiryHolder() {
    }

    /** 记录当前请求 token 的过期时刻（毫秒）；传 null 表示无 token */
    public static void set(Long expiresAtMillis) {
        if (expiresAtMillis == null) {
            EXPIRES_AT.remove();
        } else {
            EXPIRES_AT.set(expiresAtMillis);
        }
    }

    /** 当前请求 token 的过期时刻（毫秒）；无 token 返回 null */
    public static Long current() {
        return EXPIRES_AT.get();
    }

    /** 清理（必须在过滤器的 finally 里调用） */
    public static void clear() {
        EXPIRES_AT.remove();
    }
}