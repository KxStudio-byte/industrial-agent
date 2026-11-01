package com.industrial.agent.controller;

import com.industrial.agent.common.Result;
import com.industrial.agent.common.ResultCode;
import com.industrial.agent.dto.ChangePasswordRequest;
import com.industrial.agent.dto.LoginRequest;
import com.industrial.agent.exception.TokenAuthenticationException;
import com.industrial.agent.security.LoginFailGuard;
import com.industrial.agent.security.LoginUser;
import com.industrial.agent.security.PermissionConstants;
import com.industrial.agent.security.SecurityUtils;
import com.industrial.agent.service.AuthService;
import com.industrial.agent.vo.AuthPingVO;
import com.industrial.agent.vo.LoginVO;
import com.industrial.agent.vo.LogoutVO;
import com.industrial.agent.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证接口。
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>需要 token</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>/api/auth/login</td><td>否</td><td>账号密码登录，返回 JWT</td></tr>
 *   <tr><td>POST</td><td>/api/auth/logout</td><td>是</td><td>退出登录，旧 token 立即失效</td></tr>
 *   <tr><td>GET</td><td>/api/auth/me</td><td>是</td><td>当前登录用户信息</td></tr>
 *   <tr><td>PUT</td><td>/api/auth/password</td><td>是</td><td>修改自己的密码（改完所有 token 失效）</td></tr>
 *   <tr><td>GET</td><td>/api/auth/ping</td><td>是</td><td>自检：我是谁 / 有什么权限 / 何时过期</td></tr>
 *   <tr><td>POST</td><td>/api/auth/unlock/{username}</td><td>是（ADMIN）</td><td>解除某账号的登录失败锁定</td></tr>
 * </table>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginFailGuard loginFailGuard;

    /**
     * 登录。白名单接口，不需要 token。
     * <p>
     * 业务失败（密码错、账号停用）返回 HTTP 200 + 业务码（20001/20004…），
     * 与前端 {@code request.ts} 的处理方式一致：只判断 {@code body.code !== 200}。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request), "登录成功");
    }

    /**
     * 退出登录。
     * <p>
     * 即便 token 已经过期，也返回成功——退出是个幂等操作，
     * 用户点了退出却弹出「登录已失效」的报错只会让人困惑。
     */
    @PostMapping("/logout")
    public Result<LogoutVO> logout() {
        LoginUser loginUser = SecurityUtils.currentUser();
        if (loginUser == null) {
            // 走到这里通常是 token 已失效但仍进到了 Controller（公共路径兜底），直接按成功处理
            log.debug("退出登录时未取到登录用户，按已退出处理");
            return Result.success(null, "已退出登录");
        }
        return Result.success(authService.logout(loginUser), "已退出登录");
    }

    /** 当前登录用户信息（角色 / 权限 / token 过期时刻都以服务端为准） */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.success(authService.currentUser(requireLogin(), resolveTokenExpireAt()));
    }

    /** 修改自己的密码；成功后该用户所有已签发的 token 立刻失效，需重新登录 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(requireLogin(), request);
        return Result.success(null, "密码修改成功，请使用新密码重新登录");
    }

    /** 自检接口：一眼看清当前 token 的身份、权限与剩余有效期 */
    @GetMapping("/ping")
    public Result<AuthPingVO> ping() {
        LoginUser loginUser = requireLogin();
        long now = System.currentTimeMillis();
        long issuedAt = loginUser.getIssuedAtMillis() == null ? 0L : loginUser.getIssuedAtMillis();
        Long expireAt = resolveTokenExpireAt();
        long remaining = expireAt == null ? -1L : Math.max(0L, (expireAt - now) / 1000);
        return Result.success(AuthPingVO.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .realName(loginUser.getRealName())
                .roleKey(loginUser.getRoleKey())
                .roleName(loginUser.getRoleName())
                .permissions(loginUser.getPermissions().stream().sorted().collect(Collectors.joining(",")))
                .issuedAt(issuedAt)
                // 原生 long：null 会拆箱 NPE，未知时统一给 0
                .expiresAt(expireAt == null ? 0L : expireAt)
                .remainingSeconds(remaining)
                .build());
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 运维/测试用：解除某账号的登录失败锁定。
     * <p>
     * 为什么不放在上面的鉴权接口里——它不该让普通用户自助解锁，否则失败计数形同虚设。
     * 这里要求 ADMIN 的 USER:RESET_PASSWORD 权限（复用「重置密码」的授权语义：
     * 能重置别人密码的人，本来就该能解锁别人）。
     */
    @PostMapping("/unlock/{username}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_RESET_PASSWORD + "')")
    public Result<Map<String, Object>> unlock(@PathVariable String username) {
        boolean ok = loginFailGuard.unlock(username);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("unlocked", ok);
        // 解锁后立刻回读一次，把「确实已经不在锁定状态」作为证据返回
        data.put("lockedSeconds", loginFailGuard.lockedSeconds(username));
        return ok
                ? Result.success(data, "已解除账号 " + username + " 的登录锁定")
                : Result.error(ResultCode.INTERNAL_ERROR, "解锁失败：缓存服务不可用");
    }

    private LoginUser requireLogin() {
        LoginUser loginUser = SecurityUtils.currentUser();
        if (loginUser == null) {
            // 抛认证异常而不是业务异常：这样 401 语义 + 具体业务码都能传出去
            throw new TokenAuthenticationException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /**
     * 从当前请求的 token 里读出 exp。
     * <p>
     * token 本身在 {@code JwtAuthenticationFilter} 里已经解析过一次，
     * 这里再取一次 Authorization 头是刻意的：LoginUser 只保留业务字段，
     * 不让「过期时间」这种协议层信息污染领域对象。
     */
    private Long resolveTokenExpireAt() {
        return com.industrial.agent.security.TokenExpiryHolder.current();
    }
}