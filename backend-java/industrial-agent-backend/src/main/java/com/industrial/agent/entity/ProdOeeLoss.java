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
import java.time.LocalDateTime;

/**
 * OEE 损失明细（表 {@code prod_oee_loss}）。
 * <p>
 * 挂在<b>批次</b>上而不是计划上：同一条计划里不同批次的停机原因往往不同，
 * 挂到计划层就没法回答「哪一批换线超时了」。
 * <p>
 * {@code impactPct} 是对 OEE 百分点的贡献，页面直接显示成 {@code -2.0pct}。
 */
@Data
@TableName("prod_oee_loss")
public class ProdOeeLoss {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属批次ID */
    private Long batchId;

    /** 损失类型：设备停机/换线时间/质量损失/速度降低/小停机/其他 */
    private String lossType;

    /** 归因维度，如「故障 / 更换模具」 */
    private String attribution;

    /** 损失时长(分钟) */
    private Integer durationMinutes;

    /** 对 OEE 的影响(百分点) */
    private BigDecimal impactPct;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occurTime;

    /** 补充说明 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}