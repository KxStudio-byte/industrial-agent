package com.industrial.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户，对应表 {@code sys_user}。
 * <p>
 * 注意：{@code password} 存的是 BCrypt 密文（以 {@code $2a$} 开头）。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String realName;

    private Long roleId;

    private Long workshopId;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /**
     * token 版本号。
     * <p>
     * 每改一次密码、被管理员强制下线、或在新的终端登录，就 +1；
     * 签发的 token 里带着签发时的版本号，对不上即视为失效。
     * 这是无状态 JWT 实现「立即踢下线」的核心手段。
     */
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}