package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.ExamPaper;
import com.gyb.xqfx.mapper.ExamPaperMapper;
import com.gyb.xqfx.model.vo.ExamAnalysisVO;
import com.gyb.xqfx.service.ExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 试卷与知识点分析
 */
@Tag(name = "试卷分析")
@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamPaperMapper paperMapper;

    @Operation(summary = "试卷列表")
    @GetMapping("/papers")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ExamPaper>> papers(@RequestParam(required = false) Long termId) {
        return Result.success(paperMapper.selectList(new LambdaQueryWrapper<ExamPaper>()
                .eq(termId != null, ExamPaper::getTermId, termId)
                .orderByDesc(ExamPaper::getId)));
    }

    @Operation(summary = "试卷分析结果")
    @GetMapping("/papers/{id}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<ExamAnalysisVO> analysis(@PathVariable Long id) {
        return Result.success(examService.getAnalysis(id));
    }

    @Operation(summary = "重新计算试卷统计（难度/区分度/知识点掌握度）")
    @PostMapping("/papers/{id}/analyze")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<ExamAnalysisVO> analyze(@PathVariable Long id) {
        return Result.success(examService.analyze(id), "分析完成");
    }

    @Operation(summary = "按知识点汇总掌握度")
    @GetMapping("/papers/{id}/knowledge")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ExamAnalysisVO.KnowledgeMastery>> knowledge(@PathVariable Long id) {
        return Result.success(examService.getAnalysis(id).getKnowledge());
    }

    @Operation(summary = "生成演示试卷数据")
    @PostMapping("/demo")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Object>> demo(@RequestParam(required = false) Long termId) {
        Long paperId = examService.generateDemoPaper(termId);
        examService.analyze(paperId);
        return Result.success(Map.of("paperId", paperId), "演示试卷已生成并完成分析");
    }
}
