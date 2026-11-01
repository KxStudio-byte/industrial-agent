package com.industrial.agent.security;

import java.util.Set;

/**
 * 安全相关的路径常量。
 * <p>
 * 放行清单只在这里维护一份：{@code SecurityConfig} 用它配置 {@code permitAll}，
 * {@code JwtAuthenticationFilter} 用它跳过不必要的 token 解析。
 * 两处各写一份的话，改了一处忘了另一处就是安全漏洞或性能损耗。
 */
public final class SecurityPaths {

    private SecurityPaths() {
    }

    /** 无需认证即可访问的路径（Ant 风格） */
    public static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/logout-demo",
            "/api/hello",
            "/api/health",
            "/actuator/**",
            "/actuator/health",
            "/error",
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/favicon.ico"
    );

    /** 静态资源（前端打包产物由 Nginx / 网关托管，这里兜底） */
    public static final String[] STATIC_RESOURCES = {
            "/static/**", "/assets/**", "/index.html"
    };
}