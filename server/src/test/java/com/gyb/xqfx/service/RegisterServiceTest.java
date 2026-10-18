package com.gyb.xqfx.service;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.SysRole;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.SysUserRole;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysRoleMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 自助注册服务测试（Mapper / 密码编码器使用 Mockito 隔离）
 */
@DisplayName("自助注册服务测试")
class RegisterServiceTest {

    private StudentMapper studentMapper;
    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private StudentParentMapper studentParentMapper;
    private PasswordEncoder passwordEncoder;
    private RegisterService service;

    @BeforeEach
    void setUp() {
        studentMapper = Mockito.mock(StudentMapper.class);
        userMapper = Mockito.mock(SysUserMapper.class);
        roleMapper = Mockito.mock(SysRoleMapper.class);
        userRoleMapper = Mockito.mock(SysUserRoleMapper.class);
        studentParentMapper = Mockito.mock(StudentParentMapper.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedPassword");
        // 模拟 MyBatis-Plus insert 主键回填：插入后 id 写入实体
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            ((SysUser) inv.getArgument(0)).setId(1001L);
            return 1;
        });
        service = new RegisterService(userMapper, userRoleMapper, roleMapper,
                studentMapper, studentParentMapper, passwordEncoder);
    }

    private Student enrolledStudent(Long id, Long userId) {
        Student s = new Student();
        s.setId(id);
        s.setStudentNo("2300000001");
        s.setName("张三");
        s.setPhone("13800000001");
        s.setStatus("ENROLLED");
        s.setUserId(userId);
        return s;
    }

    private SysRole studentRole() {
        SysRole r = new SysRole();
        r.setId(1L);
        r.setRoleCode("STUDENT");
        return r;
    }

    private SysRole parentRole() {
        SysRole r = new SysRole();
        r.setId(2L);
        r.setRoleCode("PARENT");
        return r;
    }

    @Test
    @DisplayName("学生注册成功：账号开通、学号关联、角色分配")
    void registerStudent_success() {
        Student s = enrolledStudent(100L, null);
        when(studentMapper.selectOne(any())).thenReturn(s);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(studentRole());

        Long userId = service.registerStudent("2300000001", "张三", "13800000001", "abc123456");

        assertNotNull(userId);
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(userCaptor.capture());
        SysUser created = userCaptor.getValue();
        assertEquals("2300000001", created.getUsername());
        assertEquals("STUDENT", created.getUserType());
        assertEquals("张三", created.getRealName());
        assertEquals(100L, created.getStudentId());
        assertEquals("$2a$10$encodedPassword", created.getPassword());
        assertEquals(1, created.getStatus());
        // 学号关联回写
        ArgumentCaptor<Student> stuCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).updateById(stuCaptor.capture());
        assertEquals(userId, stuCaptor.getValue().getUserId());
        // 角色分配
        ArgumentCaptor<SysUserRole> urCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(urCaptor.capture());
        assertEquals(userId, urCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("学号不存在 → 404")
    void registerStudent_noSuchStudent() {
        when(studentMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("999999", "张三", "13800000001", "abc123456"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非在读状态 → 403")
    void registerStudent_notEnrolled() {
        Student s = enrolledStudent(100L, null);
        s.setStatus("GRADUATED");
        when(studentMapper.selectOne(any())).thenReturn(s);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("2300000001", "张三", "13800000001", "abc123456"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("姓名与档案不符 → 403")
    void registerStudent_wrongName() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("2300000001", "李四", "13800000001", "abc123456"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("手机号与档案不符 → 403")
    void registerStudent_wrongPhone() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("2300000001", "张三", "13900000009", "abc123456"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("学号已开通账号 → 400 拒绝重复注册")
    void registerStudent_alreadyBound() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, 5L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("2300000001", "张三", "13800000001", "abc123456"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("学号登录账号被占用 → 400")
    void registerStudent_usernameTaken() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, null));
        when(userMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("2300000001", "张三", "13800000001", "abc123456"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("密码长度不足 6 位 → 400")
    void registerStudent_shortPassword() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerStudent("2300000001", "张三", "13800000001", "12345"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("家长注册成功：PARENT 账号 + 主监护人绑定")
    void registerParent_success() {
        Student s = enrolledStudent(100L, null);
        when(studentMapper.selectOne(any())).thenReturn(s);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(studentParentMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(parentRole());

        Long userId = service.registerParent("2300000001", "张三", "13800000001",
                "张父", "FATHER", "13911112222", "abc123456");

        assertNotNull(userId);
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(userCaptor.capture());
        SysUser created = userCaptor.getValue();
        assertEquals("2300000001P1", created.getUsername());
        assertEquals("PARENT", created.getUserType());
        assertEquals("张父", created.getRealName());

        ArgumentCaptor<StudentParent> bindCaptor = ArgumentCaptor.forClass(StudentParent.class);
        verify(studentParentMapper).insert(bindCaptor.capture());
        StudentParent bind = bindCaptor.getValue();
        assertEquals(100L, bind.getStudentId());
        assertEquals(userId, bind.getParentUserId());
        assertEquals("FATHER", bind.getRelation());
        assertEquals("张父", bind.getParentName());
        assertEquals("13911112222", bind.getParentPhone());
        assertEquals(1, bind.getIsPrimary());
        assertEquals(1, bind.getStatus());
        // 家长角色分配
        verify(userRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    @DisplayName("家长注册：学生已有绑定 → 400 拒绝")
    void registerParent_alreadyBound() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, null));
        when(studentParentMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerParent("2300000001", "张三", "13800000001",
                        "张父", "FATHER", "13911112222", "abc123456"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(studentParentMapper, never()).insert(any(StudentParent.class));
    }

    @Test
    @DisplayName("家长注册：家长账号已存在 → 400")
    void registerParent_usernameTaken() {
        when(studentMapper.selectOne(any())).thenReturn(enrolledStudent(100L, null));
        when(studentParentMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerParent("2300000001", "张三", "13800000001",
                        "张父", "FATHER", "13911112222", "abc123456"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }
}
