package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import com.gyb.xqfx.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三端账号管理：批量开通教师 / 学生 / 家长登录账号
 */
@Tag(name = "账号管理")
@RestController
@RequestMapping("/sys/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final SysUserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final StudentParentMapper studentParentMapper;

    @Operation(summary = "初始化三端账号（教师/学生/家长）")
    @PostMapping("/init")
    @RequireRole("ADMIN")
    @Audit(module = "账号管理", operation = "初始化三端账号")
    public Result<Map<String, Integer>> init() {
        return Result.success(accountService.initAll(), "账号初始化完成，初始密码 " + AccountService.DEFAULT_PASSWORD);
    }

    @Operation(summary = "查询各端演示账号")
    @GetMapping("/demo")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> demo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultPassword", AccountService.DEFAULT_PASSWORD);
        data.put("teacher", sampleAccounts(List.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER")));
        data.put("student", sampleAccounts(List.of("STUDENT")));
        data.put("parent", sampleParentAccounts());
        return Result.success(data);
    }

    private List<Map<String, Object>> sampleAccounts(List<String> userTypes) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String type : userTypes) {
            List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUserType, type).last("LIMIT 2"));
            for (SysUser u : users) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("username", u.getUsername());
                m.put("password", AccountService.DEFAULT_PASSWORD);
                m.put("realName", u.getRealName());
                m.put("userType", type);
                list.add(m);
            }
        }
        return list;
    }

    private List<Map<String, Object>> sampleParentAccounts() {
        List<StudentParent> binds = studentParentMapper.selectList(null);
        List<Map<String, Object>> list = new ArrayList<>();
        binds.stream().limit(3).forEach(b -> {
            SysUser u = userMapper.selectById(b.getParentUserId());
            Student s = studentMapper.selectById(b.getStudentId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u == null ? "" : u.getUsername());
            m.put("password", AccountService.DEFAULT_PASSWORD);
            m.put("realName", b.getParentName());
            m.put("childName", s == null ? "" : s.getName());
            m.put("userType", "PARENT");
            list.add(m);
        });
        return list;
    }
}
