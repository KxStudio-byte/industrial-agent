package com.industrial.agent.security;

import com.industrial.agent.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * token 作废登记簿（基于 Redis）。
 * <p>
 * <b>设计取舍</b>：这里没有采用常见的「把每个退出的 token 塞进黑名单」——
 * 那样 Redis 会随用户数量线性膨胀，还要精确控制每个 token 的剩余 TTL。
 * 换成一个更省的做法：只记「<b>该用户在某秒之前签发的 token 全部作废</b>」，
 * 一个用户最多占一个 key，TTL 就是 token 的最长有效期，过期自动清理。
 * <p>
 * Redis 不可用时的降级策略：<b>放行</b>。认证链路不能因为缓存挂了就全体锁死，
 * 退出登录最多变成「等 token 自然过期才失效」，其余校验（版本号、账号状态）不依赖 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevokeService {

    private static final String KEY_PREFIX = "auth:revoke:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    /**
     * 作废该用户在 {@code beforeMillis}（含）之前签发的所有 token。
     *
     * @param userId       用户 ID
     * @param beforeMillis 作废时刻（毫秒）——通常直接传「当前时间」
     */
    public void revokeUntil(Long userId, long beforeMillis) {
        if (userId == null) {
            return;
        }
        long ttlSeconds = Math.max(jwtProperties.getExpireSeconds(), jwtProperties.getRememberExpireSeconds());
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + userId,
                    String.valueOf(beforeMillis),
                    Duration.ofSeconds(ttlSeconds));
            log.info("token 已作废: userId={}, 作废时刻={}", userId, beforeMillis);
        } catch (Exception e) {
            log.error("写入 token 作废标记失败（Redis 异常），退出登录将延迟到 token 自然过期生效: userId={}", userId, e);
        }
    }

    /**
     * 判断某个签发时刻的 token 是否已作废。
     *
     * @param userId        用户 ID
     * @param issuedAtMillis token 签发时刻（毫秒，秒级精度）
     * @return true = 已作废
     */
    public boolean isRevoked(Long userId, long issuedAtMillis) {
        if (userId == null) {
            return false;
        }
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            if (value == null || value.isBlank()) {
                return false;
            }
            long revokedBefore = Long.parseLong(value);
            // 关键：JWT 的 iat 只有秒级精度（会被向下取整），同一秒内「先被作废、后又登录」新签的
            // token 会落在同一秒。这里必须处理成「<= 才作废」，并把续签放在作废之后的下一秒，
            // 见 AuthService#login 里的 iat 对齐逻辑。
            return issuedAtMillis <= revokedBefore;
        } catch (NumberFormatException e) {
            log.warn("token 作废标记格式异常: userId={}", userId);
            return false;
        } catch (Exception e) {
            log.warn("读取 token 作废标记失败（Redis 异常），本次放行: userId={}", userId);
            return false;
        }
    }

    /** 清理作废标记（用户重新登录成功后调用，避免旧标记把新 token 也判死） */
    public void clear(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("清理 token 作废标记失败: userId={}", userId, e);
        }
    }

    /** 剩余锁定秒数（登录失败锁定用，同 key 规范便于运维排查） */
    public static long remainingSeconds(StringRedisTemplate template, String key) {
        Long ttl = template.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -1L : ttl;
    }
}