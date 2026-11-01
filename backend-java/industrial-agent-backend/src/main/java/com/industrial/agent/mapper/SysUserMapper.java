package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户表 Mapper。
 * <p>
 * 认证链路里有几处必须<b>绕过 MyBatis-Plus 的字段白名单</b>（比如 {@code password} 不能出现在
 * 普通 select 里，否则用户对象被随手序列化就泄露了），所以这里用注解 SQL 精确控制。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 登录专用查询：显式带出 password 密文。
     * <p>
     * 单独一个方法而不是在实体上加字段，是为了保证其它接口（比如 /me）永远拿不到密码哈希。
     */
    @Select("select id, username, password, real_name, role_id, workshop_id, status, version "
            + "from sys_user where username = #{username} and is_deleted = 0 limit 1")
    SysUser selectForLogin(@Param("username") String username);

    /** 原子地把 token 版本号 +1（改密码、强制下线时调用） */
    @Update("update sys_user set version = version + 1, update_time = now() where id = #{userId} and is_deleted = 0")
    int bumpTokenVersion(@Param("userId") Long userId);

    /** 更新密码密文，同时 bump 版本号让所有旧 token 失效 */
    @Update("update sys_user set password = #{password}, version = version + 1, update_time = now() "
            + "where id = #{userId} and is_deleted = 0")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    /** 启用 / 停用账号（停用时同时 bump 版本号） */
    @Update("<script>update sys_user set status = #{status}, update_time = now()"
            + "<if test='status == 0'>, version = version + 1</if>"
            + " where id = #{userId} and is_deleted = 0</script>")
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);
}