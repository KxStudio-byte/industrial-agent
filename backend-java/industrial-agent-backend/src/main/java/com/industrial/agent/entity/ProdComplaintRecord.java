package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户投诉记录表 {@code prod_complaint_record}，质量总览「客诉」指标来源。
 */
@Data
@TableName("prod_complaint_record")
public class ProdComplaintRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客诉单号（如 CPL-20261101-001） */
    private String complaintNo;

    /** 涉及产品 */
    private String productName;

    /** 涉及产线ID（逻辑外键 base_production_line.id） */
    private Long lineId;

    /** 投诉对应的缺陷类型 */
    private String defectType;

    /** 涉及数量（件） */
    private Integer qty;

    /** 投诉内容摘要 */
    private String content;

    /** 状态：处理中 / 已关闭 */
    private String status;

    /** 投诉发生日期 */
    private LocalDate occurDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}