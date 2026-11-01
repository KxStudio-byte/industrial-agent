package com.industrial.agent.security;

import com.industrial.agent.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数器与临时锁定。
 * <p>
 * 为什么需要它：登录接口是白名单，任何人都能无限次尝试。没有限流的话，
 * 6 位纯数字密码几分钟就能被撞开。这里做一个最小可用的防护：
 * 同一账号连续失败 {@code app.jwt.account.max-fail-count} 次，锁定
 * {@code app.jwt.account.lock-seconds} 秒。
 * <p>
 * 只按<b>账号</b>计数，不按 IP —— 生产环境还要加一层 IP 维度（网关或 Sentinel 更适合），
 * 放在这里会把服务变成有状态的，不划算。
 * <p>
 * Redis 挂了同样降级为放行（宁可放行也不要让所有人登不进来）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginFailGuard {

    private static final String KEY_PREFIX = "auth:login:fail:";

    /** 解锁标记的 key 前缀（仅供排查用，不参与判定） */
    private static final String UNLOCK_MARK_PREFIX = "auth:login:unlock:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    /** 是否处于锁定状态；返回剩余秒数（>0 表示还在锁定中） */
    public long lockedSeconds(String username) {
        try {
            Long count = getCount(username);
            if (count == null || count < jwtProperties.getAccount().getMaxFailCount()) {
                return 0;
            }
            Long ttl = redisTemplate.getExpire(key(username), java.util.concurrent.TimeUnit.SECONDS);
            return ttl == null || ttl < 0 ? jwtProperties.getAccount().getLockSeconds() : ttl;
        } catch (Exception e) {
            log.warn("查询登录锁状态失败（Redis 异常），本次放行: username={}", username, e);
            return 0;
        }
    }

    /**
     * 记录一次失败。
     *
     * @return 当前连续失败次数
     */
    public long recordFailure(String username) {
        try {
            String key = key(username);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(jwtProperties.getAccount().getLockSeconds()));
            }
            if (count != null && count >= jwtProperties.getAccount().getMaxFailCount()) {
                log.warn("账号连续登录失败 {} 次，已临时锁定: username={}", count, username);
            }
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("记录登录失败次数失败（Redis 异常）: username={}", username, e);
            return 0;
        }
    }

    /** 登录成功，清零计数 */
    public void reset(String username) {
        try {
            redisTemplate.delete(key(username));
        } catch (Exception e) {
            log.warn("清除登录失败计数失败: username={}", username, e);
        }
    }

    /**
     * 管理员手动解锁（不清零的话，用户只能干等 TTL 走完）。
     * <p>
     * 同时在 Redis 留一个短标记，方便排查「谁在什么时候解了锁」；
     * 标记写失败不影响解锁本身。
     */
    public boolean unlock(String username) {
        try {
            redisTemplate.delete(key(username));
            redisTemplate.opsForValue().set(UNLOCK_MARK_PREFIX + safeName(username), String.valueOf(System.currentTimeMillis()),
                    Duration.ofSeconds(jwtProperties.getAccount().getLockSeconds()));
            log.info("管理员手动解锁登录失败计数: username={}", username);
            return true;
        } catch (Exception e) {
            log.warn("手动解锁失败（Redis 异常）: username={}", username, e);
            return false;
        }
    }

    private String safeName(String username) {
        return username == null ? "unknown" : username.trim().toLowerCase();
    }

    private Long getCount(String username) {
        String value = redisTemplate.opsForValue().get(key(username));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String key(String username) {
        return KEY_PREFIX + (username == null ? "unknown" : username.trim().toLowerCase());
    }
}