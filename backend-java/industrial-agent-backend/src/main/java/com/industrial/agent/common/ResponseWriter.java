package com.industrial.agent.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在 Servlet 过滤器 / Security 处理器里直接写 JSON 响应的小工具。
 * <p>
 * Security 的异常发生在 MVC 之前，{@code @RestControllerAdvice} 捕获不到，
 * 所以 401/403 必须在这里手动写成统一结构，否则前端拿到的是 Spring 默认的 HTML 错误页，
 * {@code request.ts} 解析 JSON 会失败，只能弹出「网络错误」。
 */
public final class ResponseWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponseWriter() {
    }

    public static void writeJson(HttpServletResponse response, int httpStatus, int code, String msg) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        Result<Void> body = Result.error(code, msg);
        response.getWriter().write(MAPPER.writeValueAsString(body));
        response.getWriter().flush();
    }

    /** 401：未登录 / token 失效 */
    public static void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED.getCode(), msg);
    }

    /** 403：已登录但无权限 */
    public static void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN.getCode(), msg);
    }
}