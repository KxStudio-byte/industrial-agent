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
 * 系统角色，对应表 {@code sys_role}。
 * <p>
 * {@code roleKey} 是给 Spring Security 用的角色标识（ADMIN / MANAGER / OPERATOR …），
 * 会被写进 JWT 的 {@code roleKey} 声明，再由 {@code JwtAuthenticationFilter} 转成 {@code ROLE_XXX} 权限。
 */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleName;

    private String roleKey;

    /**
     * 权限点，逗号分隔（如 {@code USER:VIEW,ROLE:VIEW}）。
     * <p>
     * 为空表示该角色走 {@code PermissionRules} 里的内置默认映射；
     * 一旦填了值，就以库里的为准 —— 这样运维调整权限不用重新发版。
     */
    private String perms;

    /** 1 启用 / 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer isDeleted;
}