package com.industrial.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrial.agent.common.Result;
import com.industrial.agent.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 无权限统一处理（HTTP 403）。
 * <p>
 * 和 401 的区别很重要：<b>401 = 你是谁我不知道</b>，<b>403 = 我知道你是谁，但你没这个权限</b>。
 * 前端拿到 401 才跳登录页，拿到 403 只弹提示，不能把用户踢出去。
 * <p>
 * 还会顺带把「谁想干什么被拦了」打进日志，排查权限问题时不用猜。
 */
@Slf4j
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        String who = principal instanceof LoginUser user
                ? user.getUsername() + "(" + user.getRoleName() + ")"
                : "匿名用户";

        log.warn("权限不足 [{} {}] 用户={} 原因={}",
                request.getMethod(), request.getRequestURI(), who,
                accessDeniedException == null ? "-" : accessDeniedException.getMessage());

        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        // Content-Type 必须自带 charset，否则 Tomcat 按 ISO-8859-1 兜底，中文会截断成乱码
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String msg = ResultCode.FORBIDDEN.getMessage() + "，当前账号无法执行该操作";
        byte[] bytes = objectMapper.writeValueAsString(Result.error(ResultCode.FORBIDDEN, msg))
                .getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}