package com.industrial.agent.exception;

import com.industrial.agent.common.Result;
import com.industrial.agent.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 目标：<b>任何异常都不允许以 Spring 默认错误页/裸 500 返回</b>，一律包成
 * {@code Result{code,msg,data}}，前端拦截器才能统一提示。
 * <p>
 * 处理优先级：业务异常 → 参数校验 → 数据库 → 安全 → 兜底 Exception。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /* ==================== 业务异常 ==================== */

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        // 业务异常是可预期的，用 warn 级别，不打堆栈
        log.warn("业务异常 [{} {}] code={} msg={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /* ==================== 参数校验 ==================== */

    /** @Valid 校验 @RequestBody 失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 [{} {}] {}", request.getMethod(), request.getRequestURI(), msg);
        return Result.error(ResultCode.PARAM_ERROR, msg);
    }

    /** 表单/查询参数绑定失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败 [{} {}] {}", request.getMethod(), request.getRequestURI(), msg);
        return Result.error(ResultCode.PARAM_ERROR, msg);
    }

    /** @Validated 校验方法参数（@RequestParam 等）失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(ResultCode.PARAM_ERROR, msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(ResultCode.PARAM_ERROR, "缺少必填参数：" + e.getParameterName());
    }

    /** 请求体 JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, "请求体格式错误，请检查 JSON");
    }

    /* ==================== 数据库 ==================== */

    /** 唯一键冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return Result.error(ResultCode.DATA_ALREADY_EXISTS, "数据已存在，请勿重复提交");
    }

    /** 完整性约束（外键/非空/长度） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性异常: {}", e.getMessage());
        return Result.error(ResultCode.DB_ERROR, "数据不符合约束，请检查必填项");
    }

    @ExceptionHandler(SQLException.class)
    public Result<Void> handleSql(SQLException e) {
        log.error("SQL 异常", e);
        return Result.error(ResultCode.DB_ERROR, "数据库操作异常");
    }

    /* ==================== 安全 ==================== */

    /**
     * 登录时用户名 / 密码不正确。
     * <p>
     * <b>为什么单独处理而不落到 {@link #handleAuthentication}</b>：浏览器对 401 响应会弹出自带的
     * Basic 认证框（尤其在没有 WWW-Authenticate 头的老版本下），登录页出现这种弹窗非常尴尬。
     * 更实际的理由是：登录失败是<b>业务结果</b>而不是「会话失效」，前端不需要据此跳登录页
     * （用户本来就在登录页）。
     * <p>
     * 因此这里返回 <b>HTTP 200 + code 20001</b>，与前端 {@code request.ts} 的判断方式一致：
     * 弹 {@code msg} 提示即可。
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public Result<Void> handleInvalidCredentials(InvalidCredentialsException e, HttpServletRequest request) {
        log.warn("登录失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(ResultCode.LOGIN_FAILED);
    }

    /**
     * 无权限（{@code @PreAuthorize} 拒绝）。
     * <p>
     * <b>必须返回 HTTP 403</b>：前端 {@code request.ts} 只判断 {@code body.code}，
     * 但「无权限」和「服务出错」对调用方是两种语义，HTTP 状态码也要对上，
     * 否则网关 / 监控 / 浏览器开发者工具里看到的是一堆 200，排查时会被误导。
     *
     * @return HTTP 403 + {@code {code:403,msg:...}}
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        String who = SecurityContextHolder.getContext().getAuthentication() == null
                ? "匿名"
                : String.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        log.warn("拒绝访问 [{} {}] 用户={} 原因={}",
                request.getMethod(), request.getRequestURI(), who, e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }

    /**
     * 认证失败：由 MVC 层抛出的 {@link AuthenticationException}（如 Controller 里主动抛的 token 校验异常）。
     * <p>
     * {@link TokenAuthenticationException} 自带具体业务码（20002 非法 / 20003 过期 / 20008 已作废…），
     * 必须原样透出，否则前端区分不出「过期要重新登录」和「账号被停用」。
     * <p>
     * 注意：过滤器阶段拦截的 401 不会走到这里——那是在 DispatcherServlet 之前，
     * 由 {@code JsonAuthenticationEntryPoint} 直接写响应。
     *
     * @return HTTP 401 + {@code {code:200xx,msg:...}}
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        int code = e instanceof TokenAuthenticationException tokenEx
                ? tokenEx.getCode()
                : ResultCode.UNAUTHORIZED.getCode();
        String msg = e.getMessage() == null || e.getMessage().isBlank()
                ? ResultCode.UNAUTHORIZED.getMessage()
                : e.getMessage();
        log.warn("认证失败: code={} msg={}", code, msg);
        return Result.error(code, msg);
    }

    /* ==================== Web ==================== */

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandler(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND, "接口不存在：" + e.getRequestURL());
    }

    /**
     * Spring Boot 3 默认不会抛 NoHandlerFoundException，未匹配的路径会被 ResourceHttpRequestHandler
     * 兜住并抛出 {@link NoResourceFoundException}。必须单独处理，否则会落到兜底分支，
     * 让“接口不存在”表现为 code=500 服务器内部错误，掩盖真实原因。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在: {} {}", request.getMethod(), request.getRequestURI());
        return Result.error(ResultCode.NOT_FOUND, "接口不存在：" + request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error(ResultCode.METHOD_NOT_ALLOWED, "不支持 " + e.getMethod() + " 方法");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, "上传文件超过大小限制");
    }

    /* ==================== 兜底 ==================== */

    /**
     * 兜底处理。这里必须打完整堆栈，否则线上出问题无从排查。
     * 但返回给前端的提示要做脱敏，不能把堆栈信息吐出去。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        return Result.error(ResultCode.INTERNAL_ERROR);
    }

    /* ==================== 私有工具 ==================== */

    private static String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}