package com.industrial.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动自检。
 * <p>
 * 应用起来后立即探测 MySQL / Redis，把结果打到日志里。
 * <b>探测失败不会让应用启动失败</b>——开发环境常常是"先起服务再开虚拟机"，
 * 一上来就崩会打断前端联调。真正的健康状态以 {@code /api/health} 为准。
 */
@Slf4j
@Component
public class StartupHealthChecker implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final DataSourceProperties dataSourceProperties;
    private final Environment environment;

    public StartupHealthChecker(JdbcTemplate jdbcTemplate,
                                RedisConnectionFactory redisConnectionFactory,
                                DataSourceProperties dataSourceProperties,
                                Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.dataSourceProperties = dataSourceProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== 启动自检开始 ==========");
        log.info("激活配置: {}", String.join(",", environment.getActiveProfiles()));
        log.info("服务端口: {}", environment.getProperty("server.port", "8080"));

        checkMysql();
        checkRedis();

        log.info("========== 启动自检结束 ==========");
    }

    private void checkMysql() {
        String url = dataSourceProperties.getUrl();
        // 隐藏密码只显示库名，方便确认连的是哪个库
        String masked = url == null ? "未配置" : url.replaceAll("\\?.*$", "");
        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                    Integer.class);
            log.info("[MySQL] 连接成功  db={}  version={}  表数量={}  url={}", schema, version, tableCount, masked);
        } catch (Exception e) {
            log.warn("[MySQL] 连接失败  url={}  原因: {}", masked, rootCause(e));
            log.warn("[MySQL] 服务仍会启动，但依赖数据库的接口会报错。请检查 MySQL 是否可用、账号密码是否正确。");
        }
    }

    private void checkRedis() {
        String host = environment.getProperty("spring.data.redis.host", "localhost");
        String port = environment.getProperty("spring.data.redis.port", "6379");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            log.info("[Redis] 连接成功  {}:{}  PING -> {}", host, port, pong);
        } catch (Exception e) {
            log.warn("[Redis] 连接失败  {}:{}  原因: {}", host, port, rootCause(e));
            log.warn("[Redis] 服务仍会启动，但缓存/token 黑名单等功能不可用。");
        }
    }

    /** 取最底层的异常信息，避免只看到一层包装 */
    private static String rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}