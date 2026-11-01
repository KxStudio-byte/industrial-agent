package com.industrial.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置，对应 {@code application.yml} 的 {@code app.jwt.*}。
 * <p>
 * 生产环境必须用环境变量 {@code APP_JWT_SECRET} 覆盖默认密钥，
 * HS256 要求密钥不少于 32 字节（256 bit），否则 jjwt 0.12 会直接抛 WeakKeyException。
 */
@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥（HS256，至少 32 字节） */
    private String secret;

    /** 签发方 */
    private String issuer = "industrial-agent-platform";

    /** 有效期（秒），默认 8 小时 */
    private long expireSeconds = 28800;

    /** 勾选「记住我」时的有效期（秒），默认 7 天 */
    private long rememberExpireSeconds = 604800;

    /** 请求头名称 */
    private String header = "Authorization";

    /** token 前缀（注意末尾空格） */
    private String prefix = "Bearer ";

    /**
     * 账号安全配置。
     */
    private Account account = new Account();

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public long getRememberExpireSeconds() {
        return rememberExpireSeconds;
    }

    public void setRememberExpireSeconds(long rememberExpireSeconds) {
        this.rememberExpireSeconds = rememberExpireSeconds;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    /** 账号风控配置（登录失败锁定） */
    public static class Account {

        /** 连续失败多少次后锁定 */
        private int maxFailCount = 5;

        /** 锁定时长（秒） */
        private long lockSeconds = 300;

        public int getMaxFailCount() {
            return maxFailCount;
        }

        public void setMaxFailCount(int maxFailCount) {
            this.maxFailCount = maxFailCount;
        }

        public long getLockSeconds() {
            return lockSeconds;
        }

        public void setLockSeconds(long lockSeconds) {
            this.lockSeconds = lockSeconds;
        }
    }
}