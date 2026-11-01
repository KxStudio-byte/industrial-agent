package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产线，对应表 {@code base_production_line}。
 * <p>
 * 设备列表的 {@code lineName} 从这里 JOIN 出来。
 */
@Data
@TableName("base_production_line")
public class BaseProductionLine implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workshopId;

    /** 产线名称，如「A产线」 */
    private String lineName;

    /** 0 停用 / 1 运行 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}