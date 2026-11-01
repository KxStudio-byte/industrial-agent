package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生产批次记录（表 {@code prod_batch_record}）。
 * <p>
 * 一条计划拆成若干批次，批次才是真正落在设备上的生产单元 —— 它带 {@code equipmentId}，
 * 所以 OEE 可以被下钻到设备维度。良品/不良都在这里，质量维度因此有分子分母。
 */
@Data
@TableName("prod_batch_record")
public class ProdBatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属生产计划ID */
    private Long planId;

    /** 关联设备ID（可空） */
    private Long equipmentId;

    /** 批次号（第3批） */
    private String batchNo;

    /** 良品数 */
    private Integer goodQty;

    /** 不良数 */
    private Integer defectQty;

    /** 本批次换型耗时(分钟) */
    private Integer changeoverMinutes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}