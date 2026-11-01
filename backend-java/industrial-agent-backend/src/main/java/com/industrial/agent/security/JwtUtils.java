package com.industrial.agent.security;

import com.industrial.agent.config.JwtProperties;
import com.industrial.agent.exception.TokenAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类（jjwt 0.12.x API）。
 * <p>
 * <b>注意 0.12 的 API 变化</b>：
 * <ul>
 *   <li>签名：{@code Jwts.builder().signWith(key)}（不再传 SignatureAlgorithm）</li>
 *   <li>解析：{@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}</li>
 *   <li>取负载：{@code getPayload()}（旧版叫 getBody）</li>
 * </ul>
 * <p>
 * <b>token 载荷约定</b>：
 * <pre>
 * iss  = industrial-agent-platform    签发方，校验时必须匹配，防止别处签的 token 混进来
 * sub  = username                     用户名
 * jti  = userId                       用户 ID（放 jti 而不是自定义 claim，便于按标准工具排查）
 * iat  = 签发时间（秒）                退出登录时用它判断「签发时刻 ≤ 作废时刻」的 token
 * exp  = 过期时间
 * roleKey / roleName / workshopId / ver(tokenVersion)  自定义声明
 * </pre>
 */
@Slf4j
@Component
public class JwtUtils {

    /** 签名算法要求的最短密钥长度（256 bit = 32 字节） */
    private static final int MIN_KEY_BYTES = 32;

    /** HS256 的密钥长度上限：超过 64 字节 Keys.hmacShaKeyFor 会抛异常（它只认 HS256/384/512 三档） */
    private static final int MAX_KEY_BYTES = 64;

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtils(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        // 注意：Keys.hmacShaKeyFor 在密钥过长时也会失败（只能 32/48/64 字节），
        // 所以这里必须双向校验，否则换个长密钥就会启动即崩。
        if (keyBytes.length < MIN_KEY_BYTES || keyBytes.length > MAX_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret 长度必须是 32~64 字节（当前 " + keyBytes.length
                    + " 字节），HS256 签名要求 256~512 bit");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /* ==================== 生成 ==================== */

    /**
     * 生成 token。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param claims      额外声明（roleKey、roleName、workshopId、ver …）
     * @param expireSeconds 有效期（秒），传 0 或负数用配置默认值
     */
    public String generateToken(Long userId, String username, Map<String, Object> claims, long expireSeconds) {
        long ttl = expireSeconds > 0 ? expireSeconds : properties.getExpireSeconds();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttl * 1000);

        var builder = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(username)
                .id(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry);

        if (claims != null && !claims.isEmpty()) {
            builder.claims().add(claims).and();
        }

        return builder.signWith(secretKey).compact();
    }

    /** 生成 token（默认有效期） */
    public String generateToken(Long userId, String username, Map<String, Object> claims) {
        return generateToken(userId, username, claims, 0);
    }

    /** 生成 token（无额外声明） */
    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null, 0);
    }

    /* ==================== 解析 ==================== */

    /**
     * 严格解析：签名错误 / 过期 / 签发方不符都会抛异常。
     *
     * @throws TokenAuthenticationException 具体原因（20002 非法 / 20003 过期）
     */
    public Claims parseOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenAuthenticationException(com.industrial.agent.common.ResultCode.TOKEN_INVALID, "token 不能为空");
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("token 已过期: {}", e.getMessage());
            throw new TokenAuthenticationException(com.industrial.agent.common.ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token 非法: {}", e.getMessage());
            throw new TokenAuthenticationException(com.industrial.agent.common.ResultCode.TOKEN_INVALID);
        }
    }

    /**
     * 宽松解析。
     *
     * @return 负载；token 非法或过期时返回 {@code null}（内部工具方法用，认证链路请用 {@link #parseOrThrow}）
     */
    public Claims parse(String token) {
        try {
            return parseOrThrow(token);
        } catch (TokenAuthenticationException e) {
            return null;
        }
    }

    /** 校验 token 是否有效（签名正确且未过期） */
    public boolean validate(String token) {
        return parse(token) != null;
    }

    /** 从 token 中取用户 ID（JWT 的 jti 字段） */
    public Long getUserId(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getId() == null) {
            return null;
        }
        try {
            return Long.valueOf(claims.getId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 token 中取用户名 */
    public String getUsername(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    /* ==================== 请求头 ==================== */

    /** 从 Authorization 头里剥掉 "Bearer " 前缀；没有 token 返回 null */
    public String resolveToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String prefix = properties.getPrefix();
        if (prefix != null && !prefix.isBlank() && headerValue.startsWith(prefix)) {
            String token = headerValue.substring(prefix.length()).trim();
            return token.isEmpty() ? null : token;
        }
        // 不带前缀的裸 token 一律不认：宽松解析会让人以为鉴权坏了
        return null;
    }

    /** 从请求中取原始 Authorization 头名 */
    public String getHeaderName() {
        return properties.getHeader();
    }
}