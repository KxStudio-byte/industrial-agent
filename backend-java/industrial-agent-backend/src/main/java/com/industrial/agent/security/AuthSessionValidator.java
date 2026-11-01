package com.industrial.agent.security;

import com.industrial.agent.entity.SysRole;
import com.industrial.agent.entity.SysUser;
import com.industrial.agent.mapper.SysRoleMapper;
import com.industrial.agent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录会话校验：把「这个 token 现在还能不能用」的判断集中在这里。
 * <p>
 * 无状态 JWT 的天然缺陷是<b>签出去就收不回</b>。为了让「退出登录」「改密码」「账号停用」
 * 能立刻生效，本类在每次请求时做四件事（<b>顺序很重要</b>）：
 * <ol>
 *   <li>查库确认用户还在（{@code is_deleted = 0}）</li>
 *   <li>对比 token 里的版本号 {@code ver} 与库里的 {@code version}，不一致说明改过密码或被强制下线</li>
 *   <li>对比 token 的签发时间与 Redis 里记录的「作废时刻」，退出登录时写入</li>
 *   <li>确认账号 {@code status = 1}、角色未被停用</li>
 * </ol>
 * <b>为什么版本号要放在状态检查之前</b>：账号被停用时 {@code updateStatus} 也会 bump 版本号，
 * 如果先判 status，前端永远只会看到「账号已停用」。先判版本号语义更准确：
 * 本地凭据已经作废，引导重新登录即可。
 * 每次请求多一次主键查询 + 一次 Redis GET，代价可接受（用户表很小，且都走主键）。
 * 真要省掉这一步，可以把校验结果放进 Redis 缓存几十秒，代价是失效最多延迟这么久。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionValidator {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final TokenRevokeService tokenRevokeService;

    /**
     * 校验 token 对应的账号状态。
     *
     * @return 最新的用户实体（已确认存在、启用、未被踢下线）
     * @throws com.industrial.agent.exception.TokenAuthenticationException 校验不通过，带具体原因
     */
    public ValidatedSession validate(Long userId, Integer tokenVersion, Long issuedAtMillis) {
        // 1. 用户是否还存在（物理删除 / 逻辑删除）
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw TokenAuthErrors.accountNotFound();
        }

        // 2. 版本号：改密码 / 管理员强制下线 / 账号被停用都会 +1。
        //    刻意放在 status 之前——账号被停用时也会 bump 版本号，先报「登录已失效」比
        //    「账号已停用」更贴近用户当下该做的事（重新登录）。
        int currentVersion = user.getVersion() == null ? 0 : user.getVersion();
        int tokenVer = tokenVersion == null ? 0 : tokenVersion;
        if (tokenVer != currentVersion) {
            throw TokenAuthErrors.sessionReplaced();
        }

        // 3. 退出登录登记簿
        if (issuedAtMillis != null && tokenRevokeService.isRevoked(userId, issuedAtMillis)) {
            throw TokenAuthErrors.tokenRevoked();
        }

        // 4. 账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw TokenAuthErrors.accountDisabled();
        }

        // 5. 角色：没有角色 = 没有任何权限，直接拒绝比放行后到处 403 更容易排查
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role == null) {
            throw TokenAuthErrors.roleMissing();
        }
        if (role.getStatus() != null && role.getStatus() == 0) {
            throw TokenAuthErrors.roleDisabled();
        }
        return new ValidatedSession(user, role);
    }

    /** 校验通过后返回的最新账号信息 */
    public record ValidatedSession(SysUser user, SysRole role) {
    }
    
}