package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.annotation.RequireVip;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.enums.VipFeature;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.ExamPracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错题举一反三：用本地大模型为错题生成同类巩固练习
 */
@Tag(name = "错题举一反三")
@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamPracticeController {

    private final ExamPracticeService practiceService;

    @Operation(summary = "试卷薄弱题（得分率低于60%）")
    @GetMapping("/papers/{id}/weak-questions")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ExamPracticeService.WrongQuestion>> weakQuestions(@PathVariable("id") Long paperId) {
        return Result.success(practiceService.weakQuestionsOfPaper(paperId));
    }

    @Operation(summary = "学生在某份试卷上的错题")
    @GetMapping("/papers/{id}/students/{studentId}/wrong-questions")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ExamPracticeService.WrongQuestion>> studentWrongQuestions(
            @PathVariable("id") Long paperId, @PathVariable Long studentId) {
        return Result.success(practiceService.wrongQuestionsOfStudent(paperId, studentId));
    }

    @Operation(summary = "举一反三：为错题生成同类巩固练习")
    @PostMapping("/practice/generate")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<ExamPracticeService.PracticeResult> generate(
            @RequestParam Long questionId,
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false, defaultValue = "3") Integer count) {
        ExamPracticeService.PracticeResult r =
                practiceService.generate(paperId, questionId, studentId, count);
        String msg = "RULE".equals(r.getSourceType())
                ? "已生成 " + r.getItems().size() + " 道练习（本地模型不可用，已降级为规则生成）"
                : "已用本地模型 " + r.getModel() + " 生成 " + r.getItems().size() + " 道巩固练习";
        return Result.success(r, msg);
    }

    @Operation(summary = "练习详情（学生仅可看自己的；学生查看需 VIP1）")
    @GetMapping("/practice/{id}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    @RequireVip(VipFeature.PRACTICE)
    public Result<ExamPracticeService.PracticeResult> detail(@PathVariable Long id) {
        ExamPracticeService.PracticeResult r = practiceService.detail(id);
        requireOwn(r.getStudentId());
        return Result.success(r);
    }

    @Operation(summary = "某道错题的历史生成记录")
    @GetMapping("/practice/question/{questionId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ExamPracticeService.PracticeBrief>> ofQuestion(@PathVariable Long questionId) {
        return Result.success(practiceService.practicesOfQuestion(questionId));
    }

    @Operation(summary = "我的巩固练习（学生端，仅本人）")
    @GetMapping("/practice/my")
    @RequireRole("STUDENT")
    public Result<List<ExamPracticeService.PracticeBrief>> my() {
        Long studentId = SecurityUtils.currentStudentId();
        if (studentId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定学生身份");
        }
        return Result.success(practiceService.myPractices(studentId));
    }

    /**
     * 学生身份时校验练习可见性：指派给本人的，或面向全班（studentId 为空）的练习可看
     */
    private void requireOwn(Long studentId) {
        if (SecurityUtils.hasAnyRole("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER")) {
            return;
        }
        Long myId = SecurityUtils.currentStudentId();
        if (studentId == null || studentId.equals(myId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看自己的巩固练习");
    }
}
