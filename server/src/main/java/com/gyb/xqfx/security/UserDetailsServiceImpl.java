package com.gyb.xqfx.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从数据库加载用户信息与角色权限
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final com.gyb.xqfx.service.RoleService roleService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectAuthByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // 合并「用户类型 + 已分配角色」两路角色来源
        List<SimpleGrantedAuthority> authorities = roleService.roleCodesOf(user.getId()).stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .toList();
        return new LoginUser(user.getId(), user.getUsername(), user.getUserType(), authorities)
                .setStudentId(roleService.studentIdOf(user.getId()))
                .setTeacherId(roleService.teacherIdOf(user.getId()));
    }
}
