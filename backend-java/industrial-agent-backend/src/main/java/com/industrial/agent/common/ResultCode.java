package com.industrial.agent.common;

/**
 * 业务状态码枚举。
 * <p>
 * 约定：
 * <ul>
 *   <li>200       成功</li>
 *   <li>4xx       客户端问题（参数、鉴权、资源不存在）</li>
 *   <li>5xx       服务端问题</li>
 *   <li>1xxxx     业务错误（HTTP 状态码仍返回 200，前端按 code 判断）</li>
 *   <li>2xxxx     认证 / 授权错误</li>
 * </ul>
 * 前端 {@code request.ts} 里只判断 {@code body.code !== 200} 即为失败；
 * 唯一例外是 401 —— Security 的入口点会直接写 HTTP 401，前端据此跳登录页。
 */
public enum ResultCode {

    /* ==================== 通用 ==================== */
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    /* ==================== 业务（1xxxx） ==================== */
    DATA_NOT_FOUND(10001, "数据不存在"),
    DATA_ALREADY_EXISTS(10002, "数据已存在"),
    OPERATION_NOT_ALLOWED(10003, "当前状态不允许该操作"),
    DB_ERROR(10004, "数据库操作异常"),

    /* ==================== 认证（2xxxx） ==================== */
    LOGIN_FAILED(20001, "用户名或密码错误"),
    TOKEN_INVALID(20002, "Token 无效"),
    TOKEN_EXPIRED(20003, "Token 已过期"),
    ACCOUNT_DISABLED(20004, "账号已被停用"),
    ACCOUNT_LOCKED(20005, "账号已被锁定"),
    ACCOUNT_LOCKED_BY_FAIL(20006, "连续登录失败次数过多，账号已临时锁定"),
    ROLE_DISABLED(20007, "角色已停用"),
    TOKEN_REVOKED(20008, "登录状态已失效，请重新登录"),
    SESSION_REPLACED(20009, "账号已在其他终端登录，请重新登录"),
    PASSWORD_INCORRECT(20010, "原密码不正确");

    /** 业务码 */
    private final int code;

    /** 默认提示语 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}