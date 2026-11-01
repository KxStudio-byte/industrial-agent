package com.industrial.agent.controller;

import com.industrial.agent.common.Result;
import com.industrial.agent.security.PermissionConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限演示 / 自测接口。
 * <p>
 * <b>为什么需要它</b>：{@code @PreAuthorize} 的鉴权结果只有两种可观察路径——
 * 放行（200）和被拒（403）。没有这样一组固定入口时，验证「无权限时统一处理」
 * 只能拿真实业务接口去试，而业务接口还会被数据权限、参数校验干扰，测出来的东西不可信。
 * 这两个接口不碰任何业务数据，纯粹用来证明「管理员的权限能过、操作员的权限过不去」。
 * <p>
 * 鉴权走的是 {@code @EnableMethodSecurity} 打开的方法级鉴权，
 * 被拒时抛 {@code AccessDeniedException}，由 {@code JsonAccessDeniedHandler} 转成统一 403 JSON。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/demo")
public class AuthDemoController {

    /**
     * 只有 ADMIN（系统管理员）能访问。
     * <p>
     * 这里用 {@code hasAuthority} 而不是 {@code hasRole}：本项目权限是
     * 「角色 + 细粒度权限点」两级模型，权限点形如 {@code AUTH:DEMO_ADMIN}，
     * {@code hasRole('X')} 会隐式加 {@code ROLE_} 前缀，语义容易混淆。
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('" + PermissionConstants.AUTH_DEMO_ADMIN + "')")
    public Result<String> adminOnly() {
        return Result.success("你拥有 AUTH:DEMO_ADMIN 权限（仅系统管理员可见）");
    }

    /**
     * ADMIN 与 OPERATOR 都能访问的对照组。
     * <p>
     * 有它才能区分「403 是因为没有这个权限点」和「403 是因为这个接口整体坏了」。
     */
    @GetMapping("/view")
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.AUTH_DEMO_ADMIN + "','"
            + PermissionConstants.AUTH_DEMO_VIEW + "')")
    public Result<String> viewable() {
        return Result.success("你拥有 AUTH:DEMO_VIEW 权限（管理员与操作员都可访问）");
    }
}