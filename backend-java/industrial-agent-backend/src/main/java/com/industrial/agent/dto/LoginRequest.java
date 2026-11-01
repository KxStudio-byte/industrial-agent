package com.industrial.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求体。前端 {@code api/auth.ts} 的 {@code login(data)} 直接 POST 这个对象。
 */
@Data
public class LoginRequest implements Serializable {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 4, max = 64, message = "密码长度需在 4~64 位之间")
    private String password;

    /**
     * 记住我：勾选后 token 有效期延长到 7 天（{@code app.jwt.remember-expire-seconds}），
     * 不勾选按默认 8 小时。默认 false，避免前端不传时拿到一个超长有效期的 token。
     */
    private Boolean rememberMe = Boolean.FALSE;
}