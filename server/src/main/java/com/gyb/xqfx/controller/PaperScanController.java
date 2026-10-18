package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.PaperScan;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.PaperScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 试卷外部录入（图片/PDF/Word）→ AI 识别 → 试卷分析 → 题库复用
 *
 * <p>仅教师与管理员开放；学生/家长不可见（三端隔离）。</p>
 */
@Tag(name = "试卷录入与题库")
@RestController
@RequiredArgsConstructor
@RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
public class PaperScanController {

    private final PaperScanService paperScanService;

    @Operation(summary = "上传试卷并提取文本（图片/PDF/Word）")
    @PostMapping("/scan/upload")
    public Result<PaperScan> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "courseId", required = false) Long courseId,
                                    @RequestParam(value = "courseName", required = false) String courseName) {
        PaperScan scan = paperScanService.upload(file, title, courseId, courseName, SecurityUtils.currentUserId());
        return Result.success(scan);
    }

    @Operation(summary = "AI 解析题目并生成试卷分析（生成题库）")
    @PostMapping("/scan/{id}/parse")
    public Result<PaperScan> parse(@PathVariable Long id) {
        return Result.success(paperScanService.parseAndAnalyze(id));
    }

    @Operation(summary = "录入列表")
    @GetMapping("/scan/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "status", required = false) String status) {
        return Result.success(paperScanService.listScans(keyword, status));
    }

    @Operation(summary = "录入详情（含解析题目）")
    @GetMapping("/scan/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(paperScanService.detail(id));
    }

    @Operation(summary = "删除录入批次")
    @DeleteMapping("/scan/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        paperScanService.deleteScan(id, SecurityUtils.currentUserId());
        return Result.success();
    }

    @Operation(summary = "题库列表（跨批次检索）")
    @GetMapping("/bank/list")
    public Result<List<Map<String, Object>>> bankList(@RequestParam(value = "courseId", required = false) Long courseId,
                                                      @RequestParam(value = "knowledgePoint", required = false) String knowledgePoint,
                                                      @RequestParam(value = "type", required = false) String type,
                                                      @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(paperScanService.bankList(courseId, knowledgePoint, type, keyword));
    }

    @Operation(summary = "编辑题库题目（纠正 AI 识别）")
    @PutMapping("/bank/{id}")
    public Result<Void> bankEdit(@PathVariable Long id,
                                 @RequestParam(value = "stem", required = false) String stem,
                                 @RequestParam(value = "answer", required = false) String answer,
                                 @RequestParam(value = "analysis", required = false) String analysis,
                                 @RequestParam(value = "score", required = false) BigDecimal score,
                                 @RequestParam(value = "knowledgePoint", required = false) String knowledgePoint,
                                 @RequestParam(value = "difficulty", required = false) String difficulty,
                                 @RequestParam(value = "questionType", required = false) String questionType,
                                 @RequestParam(value = "optionsJson", required = false) String optionsJson) {
        paperScanService.bankEdit(id, stem, answer, analysis, score, knowledgePoint, difficulty, questionType, optionsJson);
        return Result.success();
    }

    @Operation(summary = "删除题库题目")
    @DeleteMapping("/bank/{id}")
    public Result<Void> bankDelete(@PathVariable Long id) {
        paperScanService.bankDelete(id);
        return Result.success();
    }

    @Operation(summary = "题库题目一键布置作业")
    @PostMapping("/bank/to-homework")
    public Result<Long> toHomework(@RequestParam("bankIds") List<Long> bankIds,
                                   @RequestParam("title") String title,
                                   @RequestParam(value = "courseId", required = false) Long courseId,
                                   @RequestParam(value = "courseName", required = false) String courseName,
                                   @RequestParam("clazzId") Long clazzId,
                                   @RequestParam(value = "deadline", required = false)
                                   @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline) {
        Long hwId = paperScanService.toHomework(SecurityUtils.currentUserId(), bankIds, title, courseId, courseName, clazzId, deadline);
        return Result.success(hwId);
    }
}
