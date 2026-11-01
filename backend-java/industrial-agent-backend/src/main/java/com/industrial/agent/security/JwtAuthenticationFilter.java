package com.industrial.agent.security;

import com.industrial.agent.exception.TokenAuthenticationException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * JWT 认证过滤器。
 * <p>
 * 流程：从 {@code Authorization: Bearer xxx} 取 token → 验签/查过期 → 查库校验账号状态与版本号
 * → <b>用库里的实时角色</b>重建权限 → 写入 SecurityContext。
 * <p>
 * <b>两个关键设计</b>：
 * <ol>
 *   <li><b>token 无效时不直接返回 401</b>，而是把失败原因记到 request 上继续放行，
 *       交给 {@code AuthorizationFilter} 决定：公开接口（/api/hello）带着过期 token 仍能访问；
 *       受保护接口则由 {@link JsonAuthenticationEntryPoint} 按具体原因返回 401。</li>
 *   <li><b>权限不信 token，只信数据库</b>。token 里的 roleKey 只是给前端展示用的，
 *       管理员改了角色 / 停用了角色，之前签发的 token 会立刻按新权限生效。</li>
 * </ol>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 失败原因存进 request 的属性名，供 EntryPoint 读取 */
    public static final String AUTH_ERROR_ATTR = "AUTH_ERROR";

    private final JwtUtils jwtUtils;
    private final AuthSessionValidator sessionValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtUtils jwtUtils, AuthSessionValidator sessionValidator) {
        this.jwtUtils = jwtUtils;
        this.sessionValidator = sessionValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            doAuthenticate(request, response, filterChain);
        } finally {
            // 线程池复用，必须清理，否则下一个请求会读到上一个请求的过期时刻
            TokenExpiryHolder.clear();
        }
    }

    private void doAuthenticate(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
        // 登录 / 退出这类白名单接口不需要解析 token，省掉一次查库
        if (isWhiteListed(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(jwtUtils.getHeaderName());
            String token = jwtUtils.resolveToken(header);

            if (token != null) {
                try {
                    authenticate(request, token);
                } catch (TokenAuthenticationException e) {
                    // 只记录原因，不在这里写响应：放行给后面的授权阶段判断
                    request.setAttribute(AUTH_ERROR_ATTR, e);
                    log.debug("JWT 认证失败 [{} {}]: {} ({})",
                            request.getMethod(), request.getRequestURI(), e.getMessage(), e.getCode());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        Claims claims = jwtUtils.parseOrThrow(token);
        // 过期时刻写入 ThreadLocal，供 AuthController 返回给前端做自动登出
        TokenExpiryHolder.set(claims.getExpiration() == null ? null : claims.getExpiration().getTime());

        Long userId;
        try {
            userId = claims.getId() == null ? null : Long.valueOf(claims.getId());
        } catch (NumberFormatException e) {
            throw TokenAuthErrors.invalid();
        }
        if (userId == null) {
            throw TokenAuthErrors.invalid();
        }

        Integer tokenVersion = claims.get("ver", Integer.class);
        Long issuedAtMillis = claims.getIssuedAt() == null ? null : claims.getIssuedAt().getTime();

        // 查库：账号是否还启用、是否已被踢下线、角色是否还生效
        AuthSessionValidator.ValidatedSession session = sessionValidator.validate(userId, tokenVersion, issuedAtMillis);

        LoginUser principal = PermissionRules.buildLoginUser(
                session.user(), session.role(), tokenVersion, issuedAtMillis);

        List<SimpleGrantedAuthority> authorities = principal.authorityNames().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("JWT 认证通过: userId={}, username={}, roleKey={}, 权限={}",
                userId, principal.getUsername(), principal.getRoleKey(), principal.getPermissions());
    }

    /** 白名单请求直接跳过解析（与 SecurityConfig 的放行清单保持一致） */
    private boolean isWhiteListed(String uri) {
        Set<String> patterns = SecurityPaths.PUBLIC_PATHS;
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }
}