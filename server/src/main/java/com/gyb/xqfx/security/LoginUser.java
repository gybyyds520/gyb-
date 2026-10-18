package com.gyb.xqfx.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 登录用户主体
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;
    private String userType;

    /** 关联学生ID（学生本人登录时），教师/家长为 null */
    private Long studentId;

    /** 关联教师ID（教师登录时），其他为 null */
    private Long teacherId;

    public LoginUser(Long userId, String username, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.authorities = authorities;
    }

    public LoginUser(Long userId, String username, String userType,
                     Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, authorities);
        this.userType = userType;
    }

    public LoginUser setStudentId(Long studentId) {
        this.studentId = studentId;
        return this;
    }

    public LoginUser setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
        return this;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
