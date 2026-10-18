package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.security.JwtTokenProvider;
import com.gyb.xqfx.service.RolePermissionService;
import com.gyb.xqfx.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 认证：教师端 / 学生端 / 家长端 三入口登录
 *
 * <p>登录时必须选择身份入口，后端校验账号实际身份与所选入口一致，
 * 避免学生账号从教师入口登录等越权情况。</p>
 */
@Slf4j
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 登录入口 → 允许的用户类型 */
    private static final Map<String, Set<String>> LOGIN_ENTRY = Map.of(
            "TEACHER", Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"),
            "STUDENT", Set.of("STUDENT"),
            "PARENT", Set.of("PARENT")
    );

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;

        /** 登录入口: TEACHER教师端 / STUDENT学生端 / PARENT家长端 */
        private String loginType;
    }

    @Operation(summary = "登录（需选择教师端/学生端/家长端入口）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = sysUserMapper.selectAuthByUsername(request.getUsername());

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        // 校验登录入口与账号实际身份一致
        String entry = request.getLoginType() == null || request.getLoginType().isBlank()
                ? "TEACHER" : request.getLoginType().toUpperCase();
        Set<String> allowed = LOGIN_ENTRY.get(entry);
        if (allowed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未知登录入口：" + entry + "，可选 TEACHER / STUDENT / PARENT");
        }
        String userType = user.getUserType();
        if (userType == null || !allowed.contains(userType)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "该账号不是" + entryName(entry) + "账号，请从正确的登录入口进入");
        }

        Set<String> roles = roleService.roleCodesOf(user.getId());
        String authorities = roles.stream().map(r -> "ROLE_" + r)
                .reduce((a, b) -> a + "," + b).orElse("ROLE_" + userType);

        Long studentId = roleService.studentIdOf(user.getId());
        Long teacherId = roleService.teacherIdOf(user.getId());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("studentId", studentId);
        extra.put("teacherId", teacherId);

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), userType, authorities, extra);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // 更新最后登录信息
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("expiresIn", jwtTokenProvider.getExpiration());
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("userType", userType);
        data.put("loginType", entry);
        data.put("roles", roles.stream().toList());
        data.put("menus", rolePermissionService.visibleMenus(userType));
        data.put("studentId", studentId);
        data.put("teacherId", teacherId);
        return Result.success(data, "登录成功");
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestHeader("Authorization") String header) {
        String token = header.startsWith("Bearer ") ? header.substring(7) : header;
        if (!jwtTokenProvider.validate(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token 无效或已过期");
        }
        String username = jwtTokenProvider.getUsername(token);
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        String authorities = roleService.authoritiesOf(user.getId());
        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getUserType(), authorities);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", accessToken);
        data.put("expiresIn", jwtTokenProvider.getExpiration());
        return Result.success(data);
    }

    @Operation(summary = "获取当前登录用户信息与可见菜单")
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader("Authorization") String header) {
        String token = header.startsWith("Bearer ") ? header.substring(7) : header;
        Long userId = jwtTokenProvider.getUserId(token);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Set<String> roles = roleService.roleCodesOf(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("userType", user.getUserType());
        data.put("email", user.getEmail());
        data.put("roles", roles.stream().toList());
        data.put("menus", rolePermissionService.visibleMenus(user.getUserType()));
        data.put("studentId", roleService.studentIdOf(userId));
        data.put("teacherId", roleService.teacherIdOf(userId));
        return Result.success(data);
    }

    @Operation(summary = "查询各登录入口支持的功能范围（权限说明）")
    @GetMapping("/permission-matrix")
    public Result<Map<String, Object>> permissionMatrix() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entries", List.of(
                Map.of("code", "TEACHER", "name", "教师端", "types", LOGIN_ENTRY.get("TEACHER")),
                Map.of("code", "STUDENT", "name", "学生端", "types", LOGIN_ENTRY.get("STUDENT")),
                Map.of("code", "PARENT", "name", "家长端", "types", LOGIN_ENTRY.get("PARENT"))));
        data.put("matrix", rolePermissionService.fullMatrix());
        return Result.success(data);
    }

    private String entryName(String entry) {
        return switch (entry) {
            case "STUDENT" -> "学生端";
            case "PARENT" -> "家长端";
            default -> "教师端";
        };
    }
}
