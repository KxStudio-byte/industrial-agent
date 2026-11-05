package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车间（A车间 / B车间 / C车间）。
 * <p>
 * 只读用途：能耗模块需要把 {@code energy_meter.workshop_id} 翻译成展示名，
 * 以及按车间名反查 id。不提供增删改。
 */
@Data
@TableName("sys_workshop")
public class SysWorkshop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workshopName;

    /** 车间主管用户 id */
    private Long managerId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}