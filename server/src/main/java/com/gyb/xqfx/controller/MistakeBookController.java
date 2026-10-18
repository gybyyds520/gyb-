package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.MistakeBook;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.DataScopeService;
import com.gyb.xqfx.service.MistakeBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 错题本（装载作业帮「错题本」能力）
 */
@Tag(name = "错题本")
@RestController
@RequestMapping("/mistake")
@RequiredArgsConstructor
public class MistakeBookController {

    private final MistakeBookService mistakeBookService;
    private final DataScopeService dataScopeService;

    @Data
    public static class AddRequest {
        @NotBlank(message = "题目内容不能为空")
        private String questionContent;
        private Long courseId;
        private String courseName;
        private String knowledgePoint;
        private String questionType;
        private String wrongAnswer;
        private String rightAnswer;
        private String mistakeReason;
    }

    @Operation(summary = "手动收录错题（学生本人或教师代录）")
    @PostMapping
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<Long> add(@Valid @RequestBody AddRequest req) {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            throw new com.gyb.xqfx.common.BusinessException(
                    com.gyb.xqfx.common.ErrorCode.FORBIDDEN, "学生端操作需先登录学生账号");
        }
        return Result.success(mistakeBookService.add(studentId, req.getCourseId(), req.getCourseName(),
                req.getKnowledgePoint(), req.getQuestionType(), req.getQuestionContent(),
                req.getWrongAnswer(), req.getRightAnswer(), "MANUAL", null, req.getMistakeReason()));
    }

    @Operation(summary = "我的错题本（按课程分组）")
    @GetMapping("/my")
    @RequireRole({"STUDENT"})
    public Result<List<Map<String, Object>>> myList() {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            throw new com.gyb.xqfx.common.BusinessException(
                    com.gyb.xqfx.common.ErrorCode.FORBIDDEN, "请使用学生账号");
        }
        return Result.success(mistakeBookService.myList(studentId));
    }

    @Operation(summary = "我的错题统计（总量/知识点/错因分布）")
    @GetMapping("/my/stats")
    @RequireRole({"STUDENT"})
    public Result<Map<String, Object>> myStats() {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            throw new com.gyb.xqfx.common.BusinessException(
                    com.gyb.xqfx.common.ErrorCode.FORBIDDEN, "请使用学生账号");
        }
        return Result.success(mistakeBookService.stats(studentId));
    }

    @Operation(summary = "教师查看某学生错题（数据范围校验）")
    @GetMapping("/student/{studentId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<Map<String, Object>>> listOfStudent(@PathVariable Long studentId) {
        return Result.success(mistakeBookService.listOfStudent(studentId));
    }

    @Operation(summary = "标记错题已掌握")
    @PutMapping("/{id}/mastered")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<Boolean> markMastered(@PathVariable Long id) {
        mistakeBookService.markMastered(id);
        return Result.success(true);
    }

    @Operation(summary = "删除错题")
    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<Boolean> remove(@PathVariable Long id) {
        mistakeBookService.remove(id);
        return Result.success(true);
    }
}
