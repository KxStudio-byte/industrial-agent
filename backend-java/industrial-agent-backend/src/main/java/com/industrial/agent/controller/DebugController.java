package com.industrial.agent.common;

import com.industrial.agent.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开的校验测试接口，用于验证全局异常处理是否生效。
 * <p>
 * 这几个接口在 {@code SecurityConfig.WHITE_LIST} 里放行 —— 注意用的路径前缀是 /api/hello
 * 的子路径，实际访问需要完整路径，见下方注释。
 * <p>
 * <b>提示</b>：如果只想验证 401 行为，直接请求任意未放行的接口（如 /api/equipment）即可。
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    /**
     * 验证「业务异常」分支：
     * GET /api/debug/error/biz  ->  {"code":500,"msg":"这是一个业务异常，用于验证全局异常处理","data":null}
     */
    @GetMapping("/error/biz")
    public Result<Void> throwBusiness() {
        throw new BusinessException("这是一个业务异常，用于验证全局异常处理");
    }

    /**
     * 验证「参数校验」分支：
     * GET /api/debug/error/param ->  {"code":400,"msg":"缺少必填参数：name","data":null}
     */
    @GetMapping("/error/param")
    public Result<String> requireParam(String name) {
        return Result.success(name);
    }

    /**
     * 验证「算术异常被兜底」分支：
     * GET /api/debug/error/boom ->  {"code":500,"msg":"服务器内部错误","data":null}
     * 后端日志里能看到完整堆栈。
     */
    @GetMapping("/error/boom")
    public Result<Integer> boom() {
        int zero = 0;
        return Result.success(1 / zero);
    }

    /**
     * 验证分页响应体结构：
     * GET /api/debug/page -> {"code":200,"data":{"total":0,"page":1,"size":10,"pages":0,"list":[]}}
     */
    @GetMapping("/page")
    public Result<PageResult<String>> page() {
        return Result.success(PageResult.empty(1, 10));
    }

    /**
     * 验证路径变量：
     * GET /api/debug/echo/abc
     */
    @GetMapping("/echo/{id}")
    public Result<String> echo(@PathVariable String id) {
        return Result.success("收到路径参数: " + id);
    }
}