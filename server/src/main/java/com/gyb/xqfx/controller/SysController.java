package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.AuditLog;
import com.gyb.xqfx.entity.SysConfig;
import com.gyb.xqfx.entity.SysRole;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.SysUserRole;
import com.gyb.xqfx.mapper.AuditLogMapper;
import com.gyb.xqfx.mapper.SysConfigMapper;
import com.gyb.xqfx.mapper.SysRoleMapper;
import com.gyb.xqfx.mapper.SysUserRoleMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.service.DataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 系统管理：用户、角色、参数配置、审计日志
 */
@Tag(name = "系统管理")
@RestController
@RequestMapping("/sys")
@RequiredArgsConstructor
public class SysController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysConfigMapper configMapper;
    private final AuditLogMapper auditLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;

    // ==================== 用户 ====================

    @Operation(summary = "用户分页")
    @GetMapping("/users")
    @RequireRole({"ADMIN"})
    public Result<IPage<SysUser>> users(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String keyword) {
        Page<SysUser> page = new Page<>(current, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword);
        }
        IPage<SysUser> result = userMapper.selectPage(page, wrapper);
        // 不返回密码
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    @Operation(summary = "新增用户")
    @Audit(module = "系统管理", operation = "新增用户")
    @PostMapping("/users")
    @RequireRole({"ADMIN"})
    public Result<Boolean> addUser(@RequestBody SysUser user) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode("123456"));
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setIsDeleted(0);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        return Result.success(userMapper.insert(user) > 0, "用户已创建");
    }

    @Operation(summary = "修改用户")
    @Audit(module = "系统管理", operation = "修改用户")
    @PutMapping("/users")
    @RequireRole({"ADMIN"})
    public Result<Boolean> updateUser(@RequestBody SysUser user) {
        // 空密码表示不修改
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(null);
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());
        return Result.success(userMapper.updateById(user) > 0, "已更新");
    }

    @Operation(summary = "删除用户")
    @Audit(module = "系统管理", operation = "删除用户")
    @DeleteMapping("/users/{id}")
    @RequireRole({"ADMIN"})
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        return Result.success(userMapper.deleteById(id) > 0, "已删除");
    }

    @Operation(summary = "查询用户角色")
    @GetMapping("/users/{id}/roles")
    @RequireRole({"ADMIN"})
    public Result<List<Long>> userRoles(@PathVariable Long id) {
        return Result.success(userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id))
                .stream().map(SysUserRole::getRoleId).toList());
    }

    @Operation(summary = "分配角色")
    @Audit(module = "系统管理", operation = "分配角色")
    @PostMapping("/users/{id}/roles")
    @RequireRole({"ADMIN"})
    public Result<Boolean> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        int count = 0;
        for (Long roleId : roleIds) {
            SysUserRole ref = new SysUserRole();
            ref.setUserId(id);
            ref.setRoleId(roleId);
            count += userRoleMapper.insert(ref);
        }
        return Result.success(count == roleIds.size(), "已分配 " + count + " 个角色");
    }

    // ==================== 角色 ====================

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    @RequireRole({"ADMIN"})
    public Result<List<SysRole>> roles() {
        return Result.success(roleMapper.selectList(null));
    }

    @Operation(summary = "新增或更新角色")
    @Audit(module = "系统管理", operation = "保存角色")
    @PostMapping("/roles")
    @RequireRole({"ADMIN"})
    public Result<Boolean> saveRole(@RequestBody SysRole role) {
        return Result.success(role.getId() == null
                ? roleMapper.insert(role) > 0
                : roleMapper.updateById(role) > 0);
    }

    @Operation(summary = "删除角色")
    @Audit(module = "系统管理", operation = "删除角色")
    @DeleteMapping("/roles/{id}")
    @RequireRole({"ADMIN"})
    public Result<Boolean> deleteRole(@PathVariable Long id) {
        return Result.success(roleMapper.deleteById(id) > 0, "已删除");
    }

    // ==================== 参数配置 ====================

    @Operation(summary = "参数配置列表")
    @GetMapping("/configs")
    @RequireRole({"ADMIN"})
    public Result<List<SysConfig>> configs() {
        return Result.success(configMapper.selectList(null));
    }

    @Operation(summary = "按 key 获取配置值")
    @GetMapping("/configs/{key}")
    @RequireRole({"ADMIN"})
    public Result<String> configValue(@PathVariable String key) {
        List<SysConfig> list = configMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return Result.success(list.isEmpty() ? null : list.get(0).getConfigValue());
    }

    @Operation(summary = "新增或更新配置")
    @Audit(module = "系统管理", operation = "保存参数配置")
    @PostMapping("/configs")
    @RequireRole({"ADMIN"})
    public Result<Boolean> saveConfig(@RequestBody SysConfig config) {
        return Result.success(config.getId() == null
                ? configMapper.insert(config) > 0
                : configMapper.updateById(config) > 0);
    }

    // ==================== 审计日志 ====================

    @Operation(summary = "审计日志分页")
    @GetMapping("/audit-logs")
    @RequireRole({"ADMIN"})
    public Result<IPage<AuditLog>> auditLogs(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String module) {
        Page<AuditLog> page = new Page<>(current, size);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(module != null && !module.isBlank(), AuditLog::getModule, module)
                .orderByDesc(AuditLog::getId);
        return Result.success(auditLogMapper.selectPage(page, wrapper));
    }

    // ==================== 数据权限 ====================

    @Operation(summary = "查询用户可见班级范围（null 表示不限）")
    @GetMapping("/data-scope/{userId}")
    @RequireRole({"ADMIN"})
    public Result<List<Long>> dataScope(@PathVariable Long userId) {
        return Result.success(dataScopeService.visibleClazzIds(userId));
    }
}
