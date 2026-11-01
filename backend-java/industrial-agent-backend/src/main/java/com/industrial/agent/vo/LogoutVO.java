package com.industrial.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 退出登录结果。
 * <p>
 * 返回被作废的作废时刻，前端可以在本地清 token 的同时确认服务端确实处理了，
 * 排查「为什么我退出后旧 token 还能用」这类问题时有据可查。
 */
@Data
@Builder
@AllArgsConstructor
public class LogoutVO implements Serializable {

    private Long userId;

    private String username;

    /** 该时刻之前签发的 token 已全部作废（毫秒时间戳）。原生 long = JSON 数字 */
    private long revokedBefore;

    /** 客户端应清空的本地存储键 */
    private String[] clearLocalKeys;
}