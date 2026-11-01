package com.industrial.agent.controller;

import com.industrial.agent.common.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础设施连通性检查接口。
 * <p>
 * GET /api/hello  —— 只看 Web 层是否通，不碰数据库/Redis
 * GET /api/health —— Web + MySQL + Redis 全量体检
 * <p>
 * 这两个接口在 {@code SecurityConfig.WHITE_LIST} 里放行，无需 token。
 */
@RestController
@RequestMapping("/api")
public class TestController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 由 mybatis-plus-spring-boot3-starter 传递引入的 spring-boot-starter-jdbc 提供 */
    private final JdbcTemplate jdbcTemplate;

    /** 由 spring-boot-starter-data-redis 提供 */
    private final StringRedisTemplate redisTemplate;

    public TestController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 连通性测试。返回结构：{"code":200,"msg":"...","data":"..."}
     */
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello Industrial AI Agent", "恭喜！成功连接到了 Java 后端！");
    }

    /**
     * 基础设施体检：Web / MySQL / Redis 三项。
     * <p>
     * 只要有一项 DOWN，总体返回 code=500，但 {@code data} 仍带上各部分明细，
     * 便于直接在浏览器里定位问题（不用去翻后端日志）。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>(16);
        boolean allUp = true;

        data.put("app", "UP");
        data.put("profile", System.getProperty("spring.profiles.active", "dev"));
        data.put("serverTime", LocalDateTime.now().format(TIME_FMT));

        // ---------- MySQL ----------
        Map<String, Object> db = new LinkedHashMap<>(8);
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                    Integer.class);
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name",
                    String.class);

            db.put("status", "UP");
            db.put("version", version);
            db.put("schema", schema);
            db.put("tableCount", tableCount);
            db.put("tables", tables);
        } catch (Exception e) {
            allUp = false;
            db.put("status", "DOWN");
            db.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        data.put("mysql", db);

        // ---------- Redis ----------
        Map<String, Object> redis = new LinkedHashMap<>(8);
        try {
            String key = "health:ping";
            String value = String.valueOf(System.currentTimeMillis());
            redisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(30));
            String readBack = redisTemplate.opsForValue().get(key);

            redis.put("status", value.equals(readBack) ? "UP" : "DEGRADED");
            redis.put("readBackMatch", value.equals(readBack));
            redis.put("ttlSeconds", redisTemplate.getExpire(key));
            if (!value.equals(readBack)) {
                allUp = false;
            }
        } catch (Exception e) {
            allUp = false;
            redis.put("status", "DOWN");
            redis.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        data.put("redis", redis);

        data.put("status", allUp ? "UP" : "DOWN");

        return allUp
                ? Result.success(data)
                : Result.error(500, "基础设施检查未全部通过", data);
    }
}