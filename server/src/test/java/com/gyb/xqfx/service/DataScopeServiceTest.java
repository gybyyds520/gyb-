package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Major;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 数据权限服务测试：教师 / 学生 / 家长三端可见范围隔离
 */
@DisplayName("数据权限服务测试")
class DataScopeServiceTest {

    private SysUserMapper userMapper;
    private RoleService roleService;
    private RolePermissionService rolePermissionService;
    private StudentMapper studentMapper;
    private TeacherMapper teacherMapper;
    private ClazzMapper clazzMapper;
    private MajorMapper majorMapper;
    private CourseOfferingMapper courseOfferingMapper;
    private StudentParentMapper studentParentMapper;
    private com.gyb.xqfx.mapper.ScoreMapper scoreMapper;
    private DataScopeService service;

    @BeforeEach
    void setUp() {
        userMapper = Mockito.mock(SysUserMapper.class);
        roleService = Mockito.mock(RoleService.class);
        rolePermissionService = new RolePermissionService();
        studentMapper = Mockito.mock(StudentMapper.class);
        teacherMapper = Mockito.mock(TeacherMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        majorMapper = Mockito.mock(MajorMapper.class);
        courseOfferingMapper = Mockito.mock(CourseOfferingMapper.class);
        studentParentMapper = Mockito.mock(StudentParentMapper.class);
        scoreMapper = Mockito.mock(com.gyb.xqfx.mapper.ScoreMapper.class);
        service = new DataScopeService(userMapper, roleService, rolePermissionService, studentMapper,
                scoreMapper, teacherMapper, clazzMapper, majorMapper, courseOfferingMapper,
                studentParentMapper);
    }

    private void asUser(Long userId, String userType) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUserType(userType);
        Mockito.when(userMapper.selectById(userId)).thenReturn(user);
        Mockito.when(roleService.roleCodesOf(userId)).thenReturn(Set.of(userType));
    }

    private Student student(Long id, Long clazzId, Long collegeId) {
        Student s = new Student();
        s.setId(id);
        s.setClazzId(clazzId);
        s.setCollegeId(collegeId);
        return s;
    }

    // ==================== 全量范围 ====================

    @Test
    @DisplayName("管理员拥有全量数据权限")
    void testAdminAllScope() {
        asUser(1L, "ADMIN");
        assertTrue(service.hasAllDataScope(1L));
        assertNull(service.visibleStudentIds(1L), "管理员可见学生不限");
    }

    @Test
    @DisplayName("校级管理者拥有全量数据权限")
    void testSchoolAdminAllScope() {
        asUser(2L, "SCHOOL_ADMIN");
        assertNull(service.visibleStudentIds(2L));
    }

    // ==================== 院级管理者 ====================

    @Test
    @DisplayName("院级管理者仅可见本学院学生")
    void testCollegeAdminScope() {
        asUser(3L, "COLLEGE_ADMIN");
        Mockito.when(roleService.teacherIdOf(3L)).thenReturn(10L);
        Teacher t = new Teacher();
        t.setId(10L);
        t.setCollegeId(100L);
        Mockito.when(teacherMapper.selectById(10L)).thenReturn(t);
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(
                student(1L, 1L, 100L), student(2L, 1L, 100L)));

        List<Long> ids = service.visibleStudentIds(3L);
        assertNotNull(ids);
        assertEquals(2, ids.size());
    }

    @Test
    @DisplayName("院级管理者可见班级经专业上溯学院")
    void testCollegeAdminClazz() {
        asUser(3L, "COLLEGE_ADMIN");
        Mockito.when(roleService.teacherIdOf(3L)).thenReturn(10L);
        Teacher t = new Teacher();
        t.setCollegeId(100L);
        Mockito.when(teacherMapper.selectById(10L)).thenReturn(t);
        Major m = new Major();
        m.setId(5L);
        Mockito.when(majorMapper.selectList(any())).thenReturn(List.of(m));
        Clazz c = new Clazz();
        c.setId(11L);
        Mockito.when(clazzMapper.selectList(any())).thenReturn(List.of(c));

        assertEquals(List.of(11L), service.visibleClazzIds(3L));
    }

    // ==================== 教师与辅导员 ====================

    @Test
    @DisplayName("教师可见授课班级与担任辅导员的班级学生")
    void testTeacherScope() {
        asUser(4L, "TEACHER");
        Mockito.when(roleService.teacherIdOf(4L)).thenReturn(20L);
        Clazz counselClazz = new Clazz();
        counselClazz.setId(11L);
        Mockito.when(clazzMapper.selectList(any())).thenReturn(List.of(counselClazz));
        CourseOffering offering = new CourseOffering();
        offering.setClazzId(12L);
        Mockito.when(courseOfferingMapper.selectList(any())).thenReturn(List.of(offering));
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(
                student(1L, 11L, 100L), student(2L, 12L, 100L)));

        assertEquals(2, service.visibleStudentIds(4L).size());
        assertTrue(service.visibleClazzIds(4L).containsAll(List.of(11L, 12L)));
    }

    @Test
    @DisplayName("教师未关联教师档案时不可见任何学生")
    void testTeacherWithoutProfile() {
        asUser(4L, "TEACHER");
        Mockito.when(roleService.teacherIdOf(4L)).thenReturn(null);
        assertTrue(service.visibleStudentIds(4L).isEmpty());
    }

    // ==================== 学生 ====================

    @Test
    @DisplayName("学生仅可见本人")
    void testStudentScope() {
        asUser(5L, "STUDENT");
        Mockito.when(roleService.studentIdOf(5L)).thenReturn(30L);

        assertEquals(List.of(30L), service.visibleStudentIds(5L));
        assertTrue(service.canAccessStudent(5L, 30L));
        assertFalse(service.canAccessStudent(5L, 31L), "学生不能查看他人");
    }

    @Test
    @DisplayName("学生未绑定学籍时不可见任何学生")
    void testStudentWithoutBinding() {
        asUser(5L, "STUDENT");
        Mockito.when(roleService.studentIdOf(5L)).thenReturn(null);
        assertTrue(service.visibleStudentIds(5L).isEmpty());
    }

    // ==================== 家长 ====================

    @Test
    @DisplayName("家长仅可见已绑定子女")
    void testParentScope() {
        asUser(6L, "PARENT");
        StudentParent bind = new StudentParent();
        bind.setStudentId(40L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind));

        assertEquals(List.of(40L), service.visibleStudentIds(6L));
        assertEquals(List.of(40L), service.childStudentIds(6L));
        assertTrue(service.canAccessStudent(6L, 40L));
        assertFalse(service.canAccessStudent(6L, 41L), "家长不能查看他人子女");
    }

    @Test
    @DisplayName("未绑定子女的家长不可见任何学生")
    void testParentWithoutChild() {
        asUser(6L, "PARENT");
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.visibleStudentIds(6L).isEmpty());
    }

    // ==================== 强制校验 ====================

    @Test
    @DisplayName("越权访问指定学生时抛出异常")
    void testRequireStudentAccessDenied() {
        asUser(5L, "STUDENT");
        Mockito.when(roleService.studentIdOf(5L)).thenReturn(30L);
        assertFalse(service.canAccessStudent(5L, 99L));
    }

    @Test
    @DisplayName("范围为空集合时全量角色的判定不受影响")
    void testNullStudentId() {
        asUser(1L, "ADMIN");
        assertFalse(service.canAccessStudent(1L, null));
    }

    @Test
    @DisplayName("数据范围按角色枚举返回正确取值")
    void testScopeOf() {
        assertEquals(RolePermissionService.Scope.ALL, rolePermissionService.scopeOf("ADMIN"));
        assertEquals(RolePermissionService.Scope.ALL, rolePermissionService.scopeOf("SCHOOL_ADMIN"));
        assertEquals(RolePermissionService.Scope.COLLEGE, rolePermissionService.scopeOf("COLLEGE_ADMIN"));
        assertEquals(RolePermissionService.Scope.CLAZZ, rolePermissionService.scopeOf("TEACHER"));
        assertEquals(RolePermissionService.Scope.SELF, rolePermissionService.scopeOf("STUDENT"));
        assertEquals(RolePermissionService.Scope.CHILD, rolePermissionService.scopeOf("PARENT"));
    }
}
