package com.industrial.agent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 * <ul>
 *   <li>Mapper 接口扫描</li>
 *   <li>分页插件（必须，否则 Page 查询不生效、返回全表）</li>
 *   <li>防全表更新/删除插件</li>
 *   <li>公共字段自动填充</li>
 * </ul>
 */
@Configuration
@MapperScan("com.industrial.agent.mapper")
public class MybatisPlusConfig {

    /**
     * 插件顺序有讲究：
     * <b>分页插件必须放在最后</b>，否则多插件叠加时 count 语句会被后续插件改写导致分页总数不对。
     * (官方文档：「使用多个功能需要注意顺序关系，建议：多租户 -> 动态表名 -> 分页 -> 乐观锁」)
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：指定 MySQL 方言
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数，防止前端传 size=100000 拖垮数据库
        pagination.setMaxLimit(500L);
        // 页码超过总页数时是否回到首页（false = 返回空列表）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 防止误操作全表 update / delete（不带 where 条件直接拦截报错）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }

    /**
     * 公共字段自动填充。
     * <p>
     * 实体字段需标注：
     * <pre>
     * &#64;TableField(fill = FieldFill.INSERT)       LocalDateTime createTime;
     * &#64;TableField(fill = FieldFill.INSERT_UPDATE) LocalDateTime updateTime;
     * </pre>
     * 注意：库里 20 张表大多已配 {@code DEFAULT CURRENT_TIMESTAMP}，
     * 这里填充是为了「Java 侧返回的对象里也有值」，避免插入后前端拿到 null。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}