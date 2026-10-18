package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.SysRole;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.SysUserRole;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysRoleMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.SysUserRoleMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三端账号初始化：为教师、学生、家长批量创建登录账号并完成身份绑定
 *
 * <p>此前系统仅有 admin 一个账号，学生与教师无法登录。本服务按既有
 * 学生/教师花名册生成账号，建立 sys_user ↔ student / teacher 双向关联，
 * 并按角色分配权限，使「教师端 / 学生端 / 家长端」可真实登录。</p>
 *
 * <p>初始密码统一为 {@link #DEFAULT_PASSWORD}，首次登录后应强制修改。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** 初始密码（演示环境） */
    public static final String DEFAULT_PASSWORD = "123456";

    /** 演示用层级：前 1 名教师为院级管理者，其后 2 名为辅导员 */
    private static final int COLLEGE_ADMIN_COUNT = 1;
    private static final int COUNSELOR_COUNT = 2;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final StudentParentMapper studentParentMapper;
    private final com.gyb.xqfx.mapper.ClazzMapper clazzMapper;
    private final com.gyb.xqfx.mapper.CourseOfferingMapper courseOfferingMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 全量初始化：教师账号 + 学生账号 + 家长账号与绑定
     *
     * @return 各类账号创建数量
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> initAll() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("teacher", initTeacherAccounts());
        result.put("student", initStudentAccounts());
        result.put("parent", initParentAccounts(1));
        result.put("scope", bindTeachingScope());
        log.info("三端账号初始化完成: {}", result);
        return result;
    }

    /**
     * 绑定教学范围：为课程轮流指派任课教师、为班级指派辅导员
     *
     * <p>演示数据中所有课程都挂在同一名教师名下且班级无辅导员，
     * 会导致教师端数据范围为空。本方法补全关联，使「任课教师看所授课程学生、
     * 辅导员看所带班级」的隔离规则真正可验证。</p>
     */
    public int bindTeachingScope() {
        List<Teacher> teachers = teacherMapper.selectList(null);
        if (teachers.isEmpty()) {
            return 0;
        }
        int count = 0;

        // 课程轮流指派任课教师
        List<CourseOffering> offerings = courseOfferingMapper.selectList(null);
        for (int i = 0; i < offerings.size(); i++) {
            CourseOffering o = offerings.get(i);
            Long target = teachers.get(i % teachers.size()).getId();
            if (!java.util.Objects.equals(o.getTeacherId(), target)) {
                o.setTeacherId(target);
                courseOfferingMapper.updateById(o);
                count++;
            }
        }

        // 班级指派辅导员（辅导员与院级管理者）
        List<Long> counselorIds = new ArrayList<>();
        for (Teacher t : teachers) {
            SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTeacherId, t.getId()));
            if (u != null && ("COUNSELOR".equals(u.getUserType()) || "COLLEGE_ADMIN".equals(u.getUserType()))) {
                counselorIds.add(t.getId());
            }
        }
        if (counselorIds.isEmpty()) {
            counselorIds.add(teachers.get(0).getId());
        }
        List<Clazz> clazzes = clazzMapper.selectList(null);
        for (int i = 0; i < clazzes.size(); i++) {
            Clazz c = clazzes.get(i);
            if (c.getCounselorId() == null) {
                c.setCounselorId(counselorIds.get(i % counselorIds.size()));
                clazzMapper.updateById(c);
                count++;
            }
        }
        log.info("绑定教学范围 {} 处", count);
        return count;
    }

    /**
     * 为全体教师创建账号：工号登录，按序分配院级管理者/辅导员/任课教师角色
     */
    public int initTeacherAccounts() {
        List<Teacher> teachers = teacherMapper.selectList(null);
        int count = 0;
        for (int i = 0; i < teachers.size(); i++) {
            Teacher t = teachers.get(i);
            if (t.getUserId() != null) {
                continue;
            }
            String userType = i < COLLEGE_ADMIN_COUNT ? "COLLEGE_ADMIN"
                    : i < COLLEGE_ADMIN_COUNT + COUNSELOR_COUNT ? "COUNSELOR" : "TEACHER";
            SysUser user = createUser(t.getTeacherNo(), t.getName(), userType);
            if (user == null) {
                continue;
            }
            user.setTeacherId(t.getId());
            userMapper.updateById(user);
            t.setUserId(user.getId());
            teacherMapper.updateById(t);
            assignRole(user.getId(), userType);
            count++;
        }
        log.info("创建教师账号 {} 个", count);
        return count;
    }

    /**
     * 为全体在校学生创建账号：学号登录，角色 STUDENT
     */
    public int initStudentAccounts() {
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getStatus, "ENROLLED"));
        int count = 0;
        for (Student s : students) {
            if (s.getUserId() != null) {
                continue;
            }
            SysUser user = createUser(s.getStudentNo(), s.getName(), "STUDENT");
            if (user == null) {
                continue;
            }
            user.setStudentId(s.getId());
            userMapper.updateById(user);
            s.setUserId(user.getId());
            studentMapper.updateById(s);
            assignRole(user.getId(), "STUDENT");
            count++;
        }
        log.info("创建学生账号 {} 个", count);
        return count;
    }

    /**
     * 为学生创建家长账号并完成绑定
     *
     * @param perStudent 每名学生绑定的家长数
     */
    public int initParentAccounts(int perStudent) {
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getStatus, "ENROLLED"));
        String[] relations = {"FATHER", "MOTHER"};
        int count = 0;
        for (Student s : students) {
            for (int i = 0; i < Math.max(1, perStudent); i++) {
                String username = s.getStudentNo() + "P" + (i + 1);
                String relation = i < relations.length ? relations[i] : "GUARDIAN";
                SysUser user = createUser(username, s.getName() + relationName(relation), "PARENT");
                if (user == null) {
                    continue;
                }
                assignRole(user.getId(), "PARENT");
                StudentParent bind = new StudentParent();
                bind.setStudentId(s.getId());
                bind.setParentUserId(user.getId());
                bind.setRelation(relation);
                bind.setParentName(s.getName() + relationName(relation));
                bind.setIsPrimary(i == 0 ? 1 : 0);
                bind.setStatus(1);
                bind.setCreateTime(LocalDateTime.now());
                bind.setUpdateTime(LocalDateTime.now());
                bind.setIsDeleted(0);
                studentParentMapper.insert(bind);
                count++;
            }
        }
        log.info("创建家长账号 {} 个", count);
        return count;
    }

    /**
     * 创建登录账号（已存在则复用）
     */
    private SysUser createUser(String username, String realName, String userType) {
        SysUser exist = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exist != null) {
            return null;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(realName);
        user.setUserType(userType);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setIsDeleted(0);
        userMapper.insert(user);
        return user;
    }

    /**
     * 按角色码分配角色
     */
    private void assignRole(Long userId, String roleCode) {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null) {
            return;
        }
        Long cnt = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, role.getId()));
        if (cnt != null && cnt > 0) {
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }

    private String relationName(String relation) {
        return switch (relation) {
            case "FATHER" -> "父亲";
            case "MOTHER" -> "母亲";
            default -> "监护人";
        };
    }
}
