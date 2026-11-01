package com.industrial.agent.exception;

import com.industrial.agent.common.ResultCode;
import org.springframework.security.core.AuthenticationException;

import java.io.Serial;

/**
 * token 认证失败。
 * <p>
 * <b>为什么不用框架自带的异常</b>：{@code JwtAuthenticationFilter} 里解析 token 失败时，
 * 如果只往 request 里塞一句话，{@code AuthenticationEntryPoint} 拿不到具体原因，
 * 前端就只能看到一句「未登录」，用户不知道到底是过期还是在别处登录了。
 * <p>
 * 继承 {@link AuthenticationException} 是为了让它天然被 Spring Security 当作认证失败处理。
 */
public class TokenAuthenticationException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码，如 20003 Token 已过期 */
    private final int code;

    public TokenAuthenticationException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public TokenAuthenticationException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    public TokenAuthenticationException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}