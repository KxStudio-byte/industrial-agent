package com.industrial.agent.security;

import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户；未登录返回 null */
    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof LoginUser loginUser ? loginUser : null;
    }

    /** 当前登录用户 ID；未登录抛 401 */
    public static Long currentUserId() {
        LoginUser user = currentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    /** 当前用户名；未登录抛 401 */
    public static String currentUsername() {
        LoginUser user = currentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.getUsername();
    }

    /** 是否已登录 */
    public static boolean isAuthenticated() {
        return currentUser() != null;
    }
}