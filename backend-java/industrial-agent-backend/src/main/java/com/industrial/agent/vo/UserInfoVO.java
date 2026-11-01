package com.industrial.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息。
 * <p>
 * <b>注意永远不要往这里加 password 字段</b>——它的密文也不该出库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO implements Serializable {

    private Long id;

    private String username;

    private String realName;

    private Long roleId;

    private String roleName;

    /** 角色标识 ADMIN / MANAGER / OPERATOR，前端据此控制按钮显隐 */
    private String roleKey;

    private Long workshopId;

    /** 权限点列表，前端做菜单/按钮级控制 */
    private List<String> permissions;

    /** token 过期时刻（毫秒），前端可用于自动登出倒计时。原生 long = JSON 数字；0 表示未知 */
    private long tokenExpireAt;
}