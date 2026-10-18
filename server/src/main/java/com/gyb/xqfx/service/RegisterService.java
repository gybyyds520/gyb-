package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 自助注册：家长 / 学生按系统已有档案核验身份后开通账号
 *
 * <p>注册不做公开放行，而是「以档案核验」：学号 + 姓名 + 手机号必须与
 * student 花名册完全一致才放行。学生注册开通 STUDENT 账号并回写 student.userId；
 * 家长注册额外写入 student_parent 绑定（主监护人）。教师 / 管理账号仍由管理员开通，
 * 不开放自助注册，避免放宽教师端入口破坏角色隔离。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final StudentMapper studentMapper;
    private final StudentParentMapper studentParentMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 学生自助注册：核验学号 + 姓名 + 手机号，开通 STUDENT 登录账号
     *
     * @return 新账号 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerStudent(String studentNo, String name, String phone, String password) {
        checkPassword(password);
        Student s = verifyStudent(studentNo, name, phone);
        if (s.getUserId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该学号已开通登录账号，请直接登录");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, s.getStudentNo()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该学号登录账号已被占用，请直接登录");
        }
        SysUser user = createUser(s.getStudentNo(), s.getName(), "STUDENT", password);
        user.setStudentId(s.getId());
        userMapper.updateById(user);
        s.setUserId(user.getId());
        studentMapper.updateById(s);
        assignRole(user.getId(), "STUDENT");
        log.info("学生自助注册成功: {}（{}）", s.getStudentNo(), s.getName());
        return user.getId();
    }

    /**
     * 家长自助注册：核验学生档案，创建 PARENT 账号并绑定为该学生主监护人
     *
     * @return 新账号 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerParent(String studentNo, String studentName, String studentPhone,
                               String parentName, String relation, String parentPhone, String password) {
        checkPassword(password);
        Student s = verifyStudent(studentNo, studentName, studentPhone);
        Long bindCnt = studentParentMapper.selectCount(new LambdaQueryWrapper<StudentParent>()
                .eq(StudentParent::getStudentId, s.getId())
                .eq(StudentParent::getStatus, 1)
                .eq(StudentParent::getIsDeleted, 0));
        if (bindCnt != null && bindCnt > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该学生已绑定家长账号，请直接登录");
        }
        String username = s.getStudentNo() + "P1";
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该学生的家长账号已存在，请直接登录");
        }
        String rel = (relation == null || relation.isBlank()) ? "GUARDIAN" : relation;
        SysUser user = createUser(username, parentName.trim(), "PARENT", password);
        assignRole(user.getId(), "PARENT");

        StudentParent bind = new StudentParent();
        bind.setStudentId(s.getId());
        bind.setParentUserId(user.getId());
        bind.setRelation(rel);
        bind.setParentName(parentName.trim());
        bind.setParentPhone(parentPhone == null ? null : parentPhone.trim());
        bind.setIsPrimary(1);
        bind.setStatus(1);
        bind.setCreateTime(LocalDateTime.now());
        bind.setUpdateTime(LocalDateTime.now());
        bind.setIsDeleted(0);
        studentParentMapper.insert(bind);
        log.info("家长自助注册成功: {} -> {}（{}）", username, s.getStudentNo(), parentName.trim());
        return user.getId();
    }

    /**
     * 密码规则：长度 6-32 位
     */
    private void checkPassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度需为 6-32 位");
        }
    }

    /**
     * 按学校档案核验学生身份：学号存在、在读、姓名一致、手机号一致
     */
    private Student verifyStudent(String studentNo, String name, String phone) {
        if (studentNo == null || studentNo.isBlank()
                || name == null || name.isBlank()
                || phone == null || phone.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学号、姓名与手机号均不能为空");
        }
        Student s = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo.trim()));
        if (s == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该学号的学生档案");
        }
        if (!"ENROLLED".equals(s.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该学生当前非在读状态，无法注册");
        }
        if (!s.getName().trim().equals(name.trim())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "姓名与学校档案不一致，核验未通过");
        }
        String dbPhone = s.getPhone() == null ? "" : s.getPhone().trim();
        if (dbPhone.isEmpty() || !dbPhone.equals(phone.trim())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "手机号与学校档案不一致，核验未通过");
        }
        return s;
    }

    /**
     * 创建登录账号（密码为注册时用户自设）
     */
    private SysUser createUser(String username, String realName, String userType, String password) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
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
}
