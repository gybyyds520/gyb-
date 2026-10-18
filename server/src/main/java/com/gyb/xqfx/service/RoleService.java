package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.SysRole;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.SysUserRole;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.SysRoleMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.SysUserRoleMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色解析服务：从 sys_user_role / sys_role 与 user_type 汇总用户真实角色
 *
 * <p>此前登录仅使用 user_type 生成单一角色，导致「给用户分配角色」不生效；
 * 本服务合并「用户类型 + 已分配角色」两路来源，作为鉴权的唯一依据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;

    /**
     * 用户的全部角色码（去重，含 user_type 本身）
     */
    public Set<String> roleCodesOf(Long userId) {
        Set<String> codes = new LinkedHashSet<>();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return codes;
        }
        if (user.getUserType() != null && !user.getUserType().isBlank()) {
            codes.add(user.getUserType());
        }
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (!roleIds.isEmpty()) {
            roleMapper.selectBatchIds(roleIds).stream()
                    .map(SysRole::getRoleCode)
                    .filter(c -> c != null && !c.isBlank())
                    .forEach(codes::add);
        }
        return codes;
    }

    /**
     * Spring Security  authorities 字符串，逗号分隔，带 ROLE_ 前缀
     */
    public String authoritiesOf(Long userId) {
        return roleCodesOf(userId).stream()
                .map(c -> "ROLE_" + c)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * 用户关联的学生ID（学生本人）
     */
    public Long studentIdOf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user != null && user.getStudentId() != null) {
            return user.getStudentId();
        }
        Student s = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
        return s == null ? null : s.getId();
    }

    /**
     * 用户关联的教师ID
     */
    public Long teacherIdOf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user != null && user.getTeacherId() != null) {
            return user.getTeacherId();
        }
        Teacher t = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
        return t == null ? null : t.getId();
    }
}
