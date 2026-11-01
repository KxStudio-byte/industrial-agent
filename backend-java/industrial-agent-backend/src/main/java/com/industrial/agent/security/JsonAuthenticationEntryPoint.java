package com.industrial.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrial.agent.common.Result;
import com.industrial.agent.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未登录统一处理（HTTP 401）。
 * <p>
 * Spring Security 默认返回一个 HTML 登录页，前后端分离下前端 {@code request.ts} 解析 JSON 会失败，
 * 只能弹「网络异常」。这里统一改写成项目约定的 {@code {code,msg,data}}。
 * <p>
 * <b>为什么不用 {@code @RestControllerAdvice}</b>：EntryPoint 在 DispatcherServlet 之前执行，
 * 那时还没有 HandlerExceptionResolver，全局异常处理器根本轮不到。
 * <p>
 * 同时把具体原因区分开：token 过期（20003）、被踢下线（20009）、账号停用（20004）……
 * 前端可以据此直接提示用户，而不是笼统地「请重新登录」。
 */
@Slf4j
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 过滤器阶段已经判明了具体原因，优先用它
        Object attr = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR);
        int code;
        String msg;
        if (attr instanceof com.industrial.agent.exception.TokenAuthenticationException cause) {
            code = cause.getCode();
            msg = cause.getMessage();
        } else {
            code = ResultCode.UNAUTHORIZED.getCode();
            // 注意：不能直接用 authException.getMessage()。
            // 压根没带 token 时，Spring Security 抛的是不带自定义消息的
            // InsufficientAuthenticationException，取出来是一长串英文
            // "Full authentication is required to access this resource"——
            // 前端会原样弹给用户。
            // 也不能用 ResultCode.UNAUTHORIZED.getMessage()：那是短版「未登录或登录已过期」，
            // 面向用户的完整提示在 {@link TokenAuthErrors#missing()}，两处保持一致。
            msg = TokenAuthErrors.MISSING_MESSAGE;
        }

        log.warn("未认证访问被拦截 [{} {}] code={} msg={}", request.getMethod(), request.getRequestURI(), code, msg);
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(code, msg));
    }

    private void write(HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}