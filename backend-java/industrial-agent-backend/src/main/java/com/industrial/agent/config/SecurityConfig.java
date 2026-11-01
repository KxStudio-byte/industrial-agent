package com.industrial.agent.config;

import com.industrial.agent.common.ResponseWriter;
import com.industrial.agent.security.JsonAccessDeniedHandler;
import com.industrial.agent.security.JsonAuthenticationEntryPoint;
import com.industrial.agent.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置。
 * <p>
 * 架构：<b>无状态 JWT</b>（stateless），不用 Session，不用表单登录。
 * <ul>
 *   <li>放行：登录、健康检查、错误页、OPTIONS 预检</li>
 *   <li>其余 {@code /api/**} 需要携带有效 token</li>
 *   <li>401/403 返回统一 JSON（默认是 HTML，前端解析不了）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 开启 @PreAuthorize("hasRole('ADMIN')")
public class SecurityConfig {

    /** 无需认证即可访问的路径 */
    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/api/hello",
            "/api/health",
            "/actuator/**",
            "/error",
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + 无状态 token，CSRF 无意义，关闭
                .csrf(AbstractHttpConfigurer::disable)
                // 用下面的 CorsConfigurationSource 精确控制来源
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 不用表单登录 / HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 不创建 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 异常处理：401/403 一律返回统一 JSON。
                // 这里刻意不再写内联的 lambda —— 那样会让 JsonAuthenticationEntryPoint /
                // JsonAccessDeniedHandler 两个 @Component 永远不会被用到（Spring Security
                // 只用 exceptionHandling 里配的，不会自动捡容器里的 Bean），
                // 结果就是「看起来有专门的处理器，实际上跑的是另一份代码」。
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // 授权规则
                .authorizeHttpRequests(auth -> auth
                        // 跨域预检请求直接放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(WHITE_LIST).permitAll()
                        // 其余全部需要认证
                        .anyRequest().authenticated()
                )

                // JWT 过滤器放在用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码加密器：BCrypt（sys_user.password 存的是 BCrypt 密文）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 屏蔽 Spring Boot 自动装配的默认内存用户。
     * <p>
     * 不屏蔽的话每次启动都会打印
     * {@code Using generated security password: xxxx}，那是给"零配置 Demo"用的，
     * 对 JWT 无状态项目毫无意义，反而让人误以为要拿这个密码登录。
     * 注册一个空的 {@code InMemoryUserDetailsManager} 就能让自动配置整体失效。
     */
    @Bean
    public UserDetailsService emptyUserDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    /**
     * 跨域配置。
     * <p>
     * 开发期前端是 {@code http://localhost:5173}，通过 Vite 代理转发时其实同源，
     * 但直接访问 8080（比如 Postman、独立部署的前端）时需要这个。
     * <p>
     * 注意：启用 {@code allowCredentials(true)} 时 {@code allowedOrigins} 不能用 {@code *}，
     * 必须精确列出（这里用 allowedOriginPatterns 以支持子域名）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]",
                "http://192.168.*.*:[*]"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}