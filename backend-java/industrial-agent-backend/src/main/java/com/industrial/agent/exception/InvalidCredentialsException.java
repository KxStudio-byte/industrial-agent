package com.industrial.agent.exception;

import org.springframework.security.authentication.BadCredentialsException;

import java.io.Serial;

/**
 * 登录时用户名 / 密码不正确。
 * <p>
 * <b>为什么单独一个异常（而不是继续抛 BusinessException）</b>：
 * 登录失败属于「认证失败」语义，用 Spring Security 提供的 {@link BadCredentialsException}
 * 子类表达最贴切；同时它继承了 {@link org.springframework.security.core.AuthenticationException}，
 * 如果哪天有人把登录接口也纳入过滤器链路，异常处理器仍然能正确识别为 401 类错误。
 * <p>
 * <b>关键点：用户不存在与密码错误必须抛同一个异常、返回同一句提示</b>，
 * 否则攻击者可以靠响应差异枚举出系统里有哪些用户名。
 * <p>
 * 认证链路故意<b>不区分</b>这两种情况，日志里也不记录「用户名不存在」还是「密码错」，
 * 只记「登录失败」。
 */
public class InvalidCredentialsException extends BadCredentialsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String msg) {
        super(msg);
    }
}