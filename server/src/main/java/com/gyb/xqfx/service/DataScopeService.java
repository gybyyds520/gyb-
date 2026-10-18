package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Major;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限：按角色限定「能看到哪些学生」
 *
 * <p>三端隔离的核心：教师按所带班级与所授课程看范围内学生，
 * 学生只能看自己，家长只能看已绑定子女，管理员按层级看全校或本院。</p>
 *
 * <ul>
 *   <li>ADMIN / SCHOOL_ADMIN：全校</li>
 *   <li>COLLEGE_ADMIN：本人所属学院</li>
 *   <li>COUNSELOR：担任辅导员的班级</li>
 *   <li>TEACHER：授课班级 + 担任辅导员的班级</li>
 *   <li>STUDENT：仅本人</li>
 *   <li>PARENT：仅已绑定子女</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    /** 可见全部数据的角色 */
    private static final List<String> ALL_DATA_ROLES = List.of("ADMIN", "SCHOOL_ADMIN");

    private final SysUserMapper userMapper;
    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final TeacherMapper teacherMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final StudentParentMapper studentParentMapper;

    /**
     * 判断用户是否拥有全量数据权限
     */
    public boolean hasAllDataScope(Long userId) {
        Set<String> roles = roleService.roleCodesOf(userId);
        return roles.stream().anyMatch(ALL_DATA_ROLES::contains);
    }

    /**
     * 用户可见班级ID列表
     *
     * @return null 表示不限（全量）
     */
    public List<Long> visibleClazzIds(Long userId) {
        String userType = userTypeOf(userId);
        RolePermissionService.Scope scope = rolePermissionService.scopeOf(userType);
        if (scope == RolePermissionService.Scope.ALL || hasAllDataScope(userId)) {
            return null;
        }
        if (scope == RolePermissionService.Scope.COLLEGE) {
            Long collegeId = collegeIdOf(userId);
            if (collegeId == null) {
                log.warn("院级管理者 {} 未关联学院，按全量处理", userId);
                return null;
            }
            // 班级不直接持有学院，需经专业上溯：clazz.major_id → major.college_id
            List<Long> majorIds = majorMapper.selectList(new LambdaQueryWrapper<Major>()
                            .eq(Major::getCollegeId, collegeId))
                    .stream().map(Major::getId).toList();
            if (majorIds.isEmpty()) {
                return List.of();
            }
            return clazzMapper.selectList(new LambdaQueryWrapper<Clazz>()
                            .in(Clazz::getMajorId, majorIds))
                    .stream().map(Clazz::getId).toList();
        }
        if (scope == RolePermissionService.Scope.CLAZZ) {
            Long teacherId = roleService.teacherIdOf(userId);
            Set<Long> ids = new LinkedHashSet<>();
            if (teacherId != null) {
                // 担任辅导员的行政班
                clazzMapper.selectList(new LambdaQueryWrapper<Clazz>()
                                .eq(Clazz::getCounselorId, teacherId))
                        .forEach(c -> ids.add(c.getId()));
                // 授课班级
                courseOfferingMapper.selectList(new LambdaQueryWrapper<CourseOffering>()
                                .eq(CourseOffering::getTeacherId, teacherId))
                        .stream().map(CourseOffering::getClazzId)
                        .filter(java.util.Objects::nonNull)
                        .forEach(ids::add);
            }
            return new ArrayList<>(ids);
        }
        // 学生与家长按学生维度限定，班级维度返回空（由 visibleStudentIds 兜底）
        return List.of();
    }

    /**
     * 用户可见的学生ID列表
     *
     * @return null 表示不限（全量）
     */
    public List<Long> visibleStudentIds(Long userId) {
        String userType = userTypeOf(userId);
        RolePermissionService.Scope scope = rolePermissionService.scopeOf(userType);
        switch (scope) {
            case ALL -> {
                return null;
            }
            case COLLEGE -> {
                Long collegeId = collegeIdOf(userId);
                if (collegeId == null) {
                    return null;
                }
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getCollegeId, collegeId))
                        .stream().map(Student::getId).toList();
            }
            case CLAZZ -> {
                Set<Long> ids = new LinkedHashSet<>();
                // 担任辅导员的行政班学生
                List<Long> clazzIds = visibleClazzIds(userId);
                if (clazzIds != null && !clazzIds.isEmpty()) {
                    studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                    .in(Student::getClazzId, clazzIds))
                            .forEach(s -> ids.add(s.getId()));
                }
                // 选修本人所授课程的学生（课程为跨班选修时不隶属于某个行政班）
                Long teacherId = roleService.teacherIdOf(userId);
                if (teacherId != null) {
                    List<Long> offeringIds = courseOfferingMapper.selectList(
                                    new LambdaQueryWrapper<CourseOffering>()
                                            .eq(CourseOffering::getTeacherId, teacherId))
                            .stream().map(CourseOffering::getId).toList();
                    if (!offeringIds.isEmpty()) {
                        scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                                        .in(Score::getCourseOfferingId, offeringIds))
                                .stream().map(Score::getStudentId)
                                .filter(java.util.Objects::nonNull)
                                .forEach(ids::add);
                    }
                }
                return new ArrayList<>(ids);
            }
            case CHILD -> {
                return childStudentIds(userId);
            }
            default -> {
                Long self = roleService.studentIdOf(userId);
                return self == null ? List.of() : List.of(self);
            }
        }
    }

    /**
     * 家长可见的子女ID列表
     */
    public List<Long> childStudentIds(Long userId) {
        return studentParentMapper.selectList(new LambdaQueryWrapper<StudentParent>()
                        .eq(StudentParent::getParentUserId, userId)
                        .eq(StudentParent::getStatus, 1))
                .stream().map(StudentParent::getStudentId).toList();
    }

    /**
     * 当前登录用户是否可访问指定学生
     */
    public boolean canAccessStudent(Long studentId) {
        return canAccessStudent(SecurityUtils.currentUserId(), studentId);
    }

    /**
     * 指定用户是否可访问指定学生
     */
    public boolean canAccessStudent(Long userId, Long studentId) {
        if (studentId == null) {
            return false;
        }
        List<Long> visible = visibleStudentIds(userId);
        if (visible == null) {
            return true;
        }
        return visible.contains(studentId);
    }

    /**
     * 强制校验：无权访问抛 403
     */
    public void requireStudentAccess(Long studentId) {
        if (!canAccessStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该学生的数据");
        }
    }

    /**
     * 当前登录学生的ID（学生端便捷方法）
     */
    public Long currentStudentId() {
        Long userId = SecurityUtils.currentUserId();
        Long self = SecurityUtils.currentStudentId();
        return self != null ? self : roleService.studentIdOf(userId);
    }

    /**
     * 查询学生列表时应用数据范围，返回需要附加的过滤条件（null 表示不限）
     */
    public List<Long> scopeForQuery() {
        return visibleStudentIds(SecurityUtils.currentUserId());
    }

    private String userTypeOf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getUserType();
    }

    private Long collegeIdOf(Long userId) {
        Long teacherId = roleService.teacherIdOf(userId);
        if (teacherId == null) {
            return null;
        }
        Teacher t = teacherMapper.selectById(teacherId);
        return t == null ? null : t.getCollegeId();
    }
}
