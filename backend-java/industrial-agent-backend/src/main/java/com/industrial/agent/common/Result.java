package com.industrial.agent.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体，与前端 {@code frontend/src/types/index.ts:ApiResult} 严格对应：
 * <pre>
 * { "code": 200, "msg": "ok", "data": {} }
 * </pre>
 * 前端 {@code request.ts} 的响应拦截器只认 {@code code === 200}，其余一律 reject 并弹 {@code msg}。
 *
 * @param <T> 业务数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态码：200 成功，其余为失败 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    private Result() {
    }

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /* ==================== 成功 ==================== */

    /** 成功，无数据 */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功，带数据 + 自定义提示语 */
    public static <T> Result<T> success(T data, String msg) {
        return new Result<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    /* ==================== 失败 ==================== */

    /** 失败，使用枚举里预置的 code + msg */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，使用枚举的 code，自定义 msg */
    public static <T> Result<T> error(ResultCode resultCode, String msg) {
        return new Result<>(resultCode.getCode(), msg, null);
    }

    /** 失败，自定义 code + msg */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /** 失败，但仍带一部分调试数据（如 /health 的数据库诊断信息） */
    public static <T> Result<T> error(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }

    /* ==================== 判断 ==================== */

    /** 是否成功（供内部调用判断）。加 @JsonIgnore 避免序列化出多余的 "success" 字段 */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}