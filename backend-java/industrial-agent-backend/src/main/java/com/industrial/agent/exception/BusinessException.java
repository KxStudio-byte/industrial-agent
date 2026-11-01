package com.industrial.agent.exception;

import com.industrial.agent.common.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 * <p>
 * 业务层主动抛出，由 {@link GlobalExceptionHandler} 统一转成 {@code Result}。
 * 用法：{@code throw new BusinessException("设备编号已存在");}
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /** 条件成立时抛异常，省掉一堆 if 括号 */
    public static void throwIf(boolean condition, String message) {
        if (condition) {
            throw new BusinessException(message);
        }
    }
}