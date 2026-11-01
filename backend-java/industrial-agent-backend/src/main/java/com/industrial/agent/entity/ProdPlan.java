package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产计划（表 {@code prod_plan}）。
 * <p>
 * 一条记录 = 某产线某天某班次要做的量。表里同时存了三个 OEE 分量的分母：
 * <ul>
 *   <li>{@code planHours} —— 开动率分母（计划工时）；</li>
 *   <li>{@code standardQtyPerHour} —— 性能效率分母（标准节拍）；</li>
 *   <li>{@code targetQty} —— 计划达成率分母。</li>
 * </ul>
 * 子实体：{@link ProdBatchRecord}（批次）、{@link ProdOeeLoss}（损失明细）。
 */
@Data
@TableName("prod_plan")
public class ProdPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计划日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planDate;

    /** 产线ID（base_production_line.id） */
    private Long lineId;

    /** 产品名称 */
    private String productName;

    /** 计划产量 */
    private Integer targetQty;

    /** 实际产量（由批次良品数汇总回填） */
    private Integer actualQty;

    /** 计划生产工时(小时)，开动率分母 */
    private BigDecimal planHours;

    /** 标准节拍(件/小时)，性能效率分母 */
    private Integer standardQtyPerHour;

    /** 班次：白班/夜班 */
    private String shiftName;

    /** 状态：待执行/执行中/已延期/已完成 */
    private String status;

    /** AI 说明（延误原因等） */
    private String aiExplanation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}