package com.industrial.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录成功后返回给前端的数据。
 * <p>
 * 前端拿到后：{@code localStorage.setItem('token', data.token)}，
 * 之后的请求由 {@code request.ts} 的请求拦截器自动带上 {@code Authorization: Bearer xxx}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {

    /** JWT token（不含 "Bearer " 前缀） */
    private String token;

    /** token 类型，固定 Bearer，方便前端拼请求头 */
    @Builder.Default
    private String tokenType = "Bearer";

    /** token 有效期（秒）。<b>原生 long</b>：前端要参与计算，不能变成字符串 */
    private long expiresIn;

    /** 过期时刻（时间戳，毫秒），前端可用它做无感刷新/倒计时。原生 long = JSON 数字 */
    private long expiresAt;

    /** 用户信息 */
    private UserInfoVO user;
}