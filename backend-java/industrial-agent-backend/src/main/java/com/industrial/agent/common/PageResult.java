package com.industrial.agent.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页响应体。
 * <p>
 * 前端当前是「全量取数 + 客户端分页」，但运行记录 / 操作流水会持续增长，
 * 因此接口设计上同时支持两种模式：
 * <ul>
 *   <li>不传 {@code page}：返回 {@code List<T>}（数组）</li>
 *   <li>传 {@code page}：返回 {@code PageResult<T>}（本类）</li>
 * </ul>
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private long total;

    /** 当前页（从 1 开始） */
    private long page;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> list;

    private PageResult() {
    }

    public static <T> PageResult<T> of(long total, long page, long size, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.total = total;
        result.page = page;
        result.size = size;
        result.pages = size > 0 ? (total + size - 1) / size : 0;
        result.list = list;
        return result;
    }

    /**
     * 由 MyBatis-Plus 的 Page 对象转换，避免在 Service 里散落转换代码。
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /** 空分页 */
    public static <T> PageResult<T> empty(long page, long size) {
        return of(0, page, size, List.of());
    }
}