package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 节能优化方案。
 * <p>
 * {@code saving} 存的是<b>每月</b>节约金额（元），与原型列表列头「预计节约 ¥28,600 / 月」一致。
 */
@Data
@TableName("energy_saving_plan")
public class EnergySavingPlan {

    /** 待执行 */
    public static final String STATUS_PENDING = "待执行";
    /** 已完成 */
    public static final String STATUS_DONE = "已完成";
    /** 执行失败 */
    public static final String STATUS_FAILED = "执行失败";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案编号，如 EO-20260907-012 */
    private String planCode;

    private String planName;

    /** 位置，如 A车间 / 4号空压机 */
    private String location;

    /** 能源介质：电 / 水 / 气 / 蒸汽 */
    private String medium;

    /** 执行状态：待执行 / 已完成 / 执行失败 */
    private String status;

    /** 预计每月节约金额（元） */
    private BigDecimal saving;

    /** AI 判断原因（原型抽屉的「异常判断」） */
    private String reason;

    /** 建议措施（原型抽屉的「AI建议」） */
    private String advice;

    /** 影响范围 */
    private String scope;

    /** 执行结果 */
    private String result;

    /** 执行时间 */
    private LocalDateTime execTime;

    /** 来源：人工 / AI Agent */
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}