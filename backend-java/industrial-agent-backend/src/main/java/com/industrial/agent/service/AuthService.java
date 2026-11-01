package com.industrial.agent.service;

import com.industrial.agent.common.ResultCode;
import com.industrial.agent.config.JwtProperties;
import com.industrial.agent.dto.ChangePasswordRequest;
import com.industrial.agent.dto.LoginRequest;
import com.industrial.agent.entity.SysRole;
import com.industrial.agent.entity.SysUser;
import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.exception.InvalidCredentialsException;
import com.industrial.agent.mapper.SysRoleMapper;
import com.industrial.agent.mapper.SysUserMapper;
import com.industrial.agent.security.JwtUtils;
import com.industrial.agent.security.LoginFailGuard;
import com.industrial.agent.security.LoginUser;
import com.industrial.agent.security.PermissionRules;
import com.industrial.agent.security.TokenRevokeService;
import com.industrial.agent.vo.LoginVO;
import com.industrial.agent.vo.LogoutVO;
import com.industrial.agent.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认证服务：登录、退出、查询当前用户、修改密码。
 * <p>
 * 所有「账号能不能登」的判断都集中在这里，Controller 只负责收参数返回结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 客户端退出登录时应当清掉的本地键（与前端 auth store 保持一致） */
    private static final String[] LOCAL_KEYS = {"token", "tokenExpireAt", "user"};

    /**
     * 一个固定的 BCrypt 密文（明文无关），仅用于「用户不存在」分支的空转比对。
     * <p>
     * 不这么做的话，不存在的用户名会立刻返回，而存在的用户名要跑一次 BCrypt（几十毫秒），
     * 攻击者用响应时间就能把用户名枚举出来。
     */
    private static final String DUMMY_BCRYPT = "$2a$10$LEdF2cG0cAOGRMRrDD4GsOefNwQExbcp9KDOr85caoSbkmHtIUqi.";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final TokenRevokeService tokenRevokeService;
    private final LoginFailGuard loginFailGuard;

    /* ==================== 登录 ==================== */

    /**
     * 账号密码登录。
     * <p>
     * 流程：登录锁定检查 → 查用户（显式带 password）→ BCrypt 比对 → 账号/角色状态检查
     * → 清掉旧的作废标记 → 签发 JWT。
     *
     * @throws BusinessException 用户名或密码错误(20001) / 账号停用(20004) / 角色停用(20007)
     *                           / 连续失败锁定(20006)
     */
    public LoginVO login(LoginRequest request) {
        String username = request.getUsername().trim();

        // 1. 连续失败锁定（防止密码暴力破解）
        long lockedSeconds = loginFailGuard.lockedSeconds(username);
        if (lockedSeconds > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED_BY_FAIL,
                    "连续登录失败次数过多，请 " + lockedSeconds + " 秒后再试");
        }

        // 2. 查用户：用户不存在与密码错误返回同一句提示，避免被用来枚举用户名
        SysUser user = sysUserMapper.selectForLogin(username);
        if (user == null) {
            // 防御性：不存在的用户名也走一次 BCrypt，让两条分支耗时接近，堵住时序侧信道
            passwordEncoder.matches(request.getPassword(), DUMMY_BCRYPT);
            long failCount = loginFailGuard.recordFailure(username);
            log.warn("登录失败（用户不存在）：username={}, 连续失败 {} 次", username, failCount);
            throw new InvalidCredentialsException(ResultCode.LOGIN_FAILED.getMessage());
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            long failCount = loginFailGuard.recordFailure(username);
            log.warn("登录失败（密码错误）：username={}, 连续失败 {} 次", username, failCount);
            throw new InvalidCredentialsException(ResultCode.LOGIN_FAILED.getMessage());
        }

        // 3. 账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 4. 角色状态（角色停用等于没有权限）
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role != null && role.getStatus() != null && role.getStatus() == 0) {
            throw new BusinessException(ResultCode.ROLE_DISABLED);
        }
        String roleKey = role == null ? "OPERATOR" : role.getRoleKey();

        // 5. 登录成功：清零失败计数
        loginFailGuard.reset(username);

        // 6. 签发 token。
        //    先清掉该用户历史上的作废标记，否则「退出后立刻重新登录」拿到的新 token
        //    会因为在同一秒内签发而被判为已作废（JWT 的 iat 只有秒级精度）。
        tokenRevokeService.clear(user.getId());

        boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
        long ttl = rememberMe ? jwtProperties.getRememberExpireSeconds() : jwtProperties.getExpireSeconds();
        int version = user.getVersion() == null ? 0 : user.getVersion();

        Map<String, Object> claims = new HashMap<>(6);
        claims.put("roleKey", roleKey);
        claims.put("roleName", role == null ? "操作员" : role.getRoleName());
        claims.put("ver", version);
        if (user.getWorkshopId() != null) {
            claims.put("workshopId", user.getWorkshopId());
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), claims, ttl);

        log.info("登录成功：userId={}, username={}, roleKey={}, rememberMe={}, 有效期={}秒",
                user.getId(), user.getUsername(), roleKey, rememberMe, ttl);

        long now = System.currentTimeMillis();
        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(ttl)
                .expiresAt(now + ttl * 1000)
                .user(toUserInfo(user, role, now + ttl * 1000))
                .build();
    }

    /* ==================== 退出登录 ==================== */

    /**
     * 退出登录。
     * <p>
     * 无状态 JWT 本身收不回来，所以这里做两件事让旧 token <b>立刻</b>失效：
     * <ol>
     *   <li>在 Redis 记下「该用户在某时刻之前签发的 token 全部作废」</li>
     *   <li>清掉本地凭据（由前端按 {@code clearLocalKeys} 执行）</li>
     * </ol>
     * 不 bump 版本号是有意的：版本号是「全端下线」的语义（改密码/封号），
     * 退出登录只踢掉当前这一轮签发，别的设备上的 token 该留还得留（多端登录场景）。
     */
    public LogoutVO logout(LoginUser loginUser) {
        long now = System.currentTimeMillis();
        tokenRevokeService.revokeUntil(loginUser.getUserId(), now);
        log.info("用户退出登录：userId={}, username={}", loginUser.getUserId(), loginUser.getUsername());
        return LogoutVO.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .revokedBefore(now)
                .clearLocalKeys(LOCAL_KEYS)
                .build();
    }

    /* ==================== 当前用户 ==================== */

    /**
     * 用 token 里的 userId 反查最新用户信息。
     * <p>
     * 角色和权限<b>以库为准</b>而不是取 token 里的快照——管理员刚把某人降级，
     * 他的旧 token 不应该还带着旧权限。
     *
     * @param tokenExpireAt 可选，token 过期时刻（前端做自动登出用）
     */
    public UserInfoVO currentUser(LoginUser loginUser, Long tokenExpireAt) {
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        return toUserInfo(user, role, tokenExpireAt);
    }

    public UserInfoVO currentUser(LoginUser loginUser) {
        return currentUser(loginUser, null);
    }

    /* ==================== 修改密码 ==================== */

    /**
     * 修改自己的密码。
     * <p>
     * 改完密码后 {@code version + 1}，所有已签发的 token 立刻失效（含当前这个），
     * 前端需要引导用户用新密码重新登录——这是安全上的正确做法：
     * 密码可能已经在别处泄露，改密码就是要让攻击者手里的 token 一起作废。
     */
    public void changePassword(LoginUser loginUser, ChangePasswordRequest request) {
        SysUser user = sysUserMapper.selectForLogin(loginUser.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        String encoded = passwordEncoder.encode(request.getNewPassword());
        sysUserMapper.updatePassword(loginUser.getUserId(), encoded);
        tokenRevokeService.clear(loginUser.getUserId());
        log.info("密码已修改，旧 token 全部失效：userId={}", loginUser.getUserId());
    }

    /* ==================== 内部工具 ==================== */

    private UserInfoVO toUserInfo(SysUser user, SysRole role, Long tokenExpireAt) {
        String roleKey = role == null ? "OPERATOR" : role.getRoleKey();
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roleId(user.getRoleId())
                .roleName(role == null ? "操作员" : role.getRoleName())
                .roleKey(roleKey)
                .workshopId(user.getWorkshopId())
                .permissions(new ArrayList<>(PermissionRules.permissionsOf(role)))
                // tokenExpireAt 是原生 long，null 会触发拆箱 NPE，这里统一兜成 0（表示未知）
                .tokenExpireAt(tokenExpireAt == null ? 0L : tokenExpireAt)
                .build();
    }

    /** 生成随机初始密码（管理员重置密码时给用户的临时口令） */
    public static String randomPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        UUID seed = UUID.randomUUID();
        java.util.Random random = new java.util.Random(seed.getMostSignificantBits() ^ seed.getLeastSignificantBits());
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** 权限点转成给前端看的数组（保序，便于对比） */
    public List<String> permissionsOf(String roleKey) {
        return new ArrayList<>(PermissionRules.permissionsOf(roleKey));
    }
}