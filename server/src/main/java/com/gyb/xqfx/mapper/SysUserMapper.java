package com.gyb.xqfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gyb.xqfx.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper
 *
 * <p>注意：SysUser#password 标注了 {@code @TableField(select = false)}，
 * 默认查询不会返回密码列，认证场景需使用 {@link #selectAuthByUsername}。</p>
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 认证用：显式查询包含密码列的完整记录
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    SysUser selectAuthByUsername(@Param("username") String username);
}
