package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.DataScopeService;
import com.gyb.xqfx.service.HomeworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作业闭环（装载作业帮「AI 秒批作业 + 学练测」能力）
 *
 * <p>教师端布置/批改、学生端作答、家长端子女作业动态；客观题自动判分，
 * 错题自动收录错题本，文本题支持 AI 批改。</p>
 */
@Tag(name = "作业管理")
@RestController
@RequestMapping("/homework")
@RequiredArgsConstructor
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final DataScopeService dataScopeService;

    @Data
    public static class CreateRequest {
        @NotBlank(message = "请输入作业标题")
        private String title;
        private Long courseId;
        private String courseName;
        private Long clazzId;
        /** 题目数组 [{no,type,content,answer,score,knowledgePoint}] */
        private List<HomeworkService.Question> questions;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime deadline;
    }

    @Data
    public static class SubmitRequest {
        /** 答案数组 [{no,answer}] */
        private List<HomeworkService.Answer> answers;
    }

    @Data
    public static class GradeRequest {
        private BigDecimal manualScore;
        private String feedback;
    }

    @Operation(summary = "教师布置作业")
    @PostMapping
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Long> create(@Valid @RequestBody CreateRequest req) {
        Long teacherId = SecurityUtils.currentUserId();
        return Result.success(homeworkService.create(teacherId, req.getTitle(), req.getCourseId(),
                req.getCourseName(), req.getClazzId(), req.getQuestions(), req.getDeadline()), "作业已布置");
    }

    @Operation(summary = "教师端：班级作业列表（含完成度）")
    @GetMapping("/teacher")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<Map<String, Object>>> teacherList(@RequestParam(required = false) Long clazzId) {
        return Result.success(homeworkService.teacherHomeworks(SecurityUtils.currentUserId(), clazzId));
    }

    @Operation(summary = "教师端：作业详情（含提交明细）")
    @GetMapping("/teacher/{homeworkId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Object>> teacherDetail(@PathVariable Long homeworkId) {
        return Result.success(homeworkService.detailForTeacher(homeworkId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "教师人工补批文本题")
    @PostMapping("/{homeworkId}/grade/{submissionId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Boolean> grade(@PathVariable Long homeworkId, @PathVariable Long submissionId,
                                 @RequestBody GradeRequest req) {
        homeworkService.grade(SecurityUtils.currentUserId(), homeworkId, submissionId,
                req.getManualScore(), req.getFeedback());
        return Result.success(true, "批改完成");
    }

    @Operation(summary = "AI 批改文本题（本地大模型评分+评语）")
    @PostMapping("/{homeworkId}/ai-grade/{submissionId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Object>> aiGrade(@PathVariable Long homeworkId, @PathVariable Long submissionId) {
        return Result.success(homeworkService.aiGrade(SecurityUtils.currentUserId(), homeworkId, submissionId));
    }

    @Operation(summary = "学生端：我的作业")
    @GetMapping("/my")
    @RequireRole({"STUDENT"})
    public Result<List<Map<String, Object>>> myHomework() {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            throw new com.gyb.xqfx.common.BusinessException(
                    com.gyb.xqfx.common.ErrorCode.FORBIDDEN, "请使用学生账号");
        }
        return Result.success(homeworkService.myHomework(studentId));
    }

    @Operation(summary = "学生端：作业详情（含题目）")
    @GetMapping("/{homeworkId}/student")
    @RequireRole({"STUDENT"})
    public Result<Map<String, Object>> studentDetail(@PathVariable Long homeworkId) {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            throw new com.gyb.xqfx.common.BusinessException(
                    com.gyb.xqfx.common.ErrorCode.FORBIDDEN, "请使用学生账号");
        }
        return Result.success(homeworkService.studentDetail(homeworkId, studentId));
    }

    @Operation(summary = "学生提交作业（客观题自动批改+错题自动收录）")
    @PostMapping("/{homeworkId}/submit")
    @RequireRole({"STUDENT"})
    public Result<Boolean> submit(@PathVariable Long homeworkId, @RequestBody SubmitRequest req) {
        Long studentId = dataScopeService.currentStudentId();
        if (studentId == null) {
            throw new com.gyb.xqfx.common.BusinessException(
                    com.gyb.xqfx.common.ErrorCode.FORBIDDEN, "请使用学生账号");
        }
        homeworkService.submit(studentId, homeworkId, req.getAnswers());
        return Result.success(true, "提交成功，客观题已自动批改");
    }

    @Operation(summary = "家长端：子女作业动态")
    @GetMapping("/parent")
    @RequireRole({"PARENT"})
    public Result<List<Map<String, Object>>> parentHomework() {
        return Result.success(homeworkService.parentChildrenHomework(SecurityUtils.currentUserId()));
    }
}
