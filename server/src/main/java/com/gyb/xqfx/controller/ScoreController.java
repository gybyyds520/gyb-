package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.ImportTask;
import com.gyb.xqfx.model.excel.ScoreExcel;
import com.gyb.xqfx.model.query.ScoreQuery;
import com.gyb.xqfx.model.vo.ScoreVO;
import com.gyb.xqfx.service.ExcelImportService;
import com.gyb.xqfx.service.ScoreService;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.service.DataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 成绩管理（仅教师与管理者可操作，数据范围按登录角色限定）
 */
@Tag(name = "学情数据-成绩")
@RestController
@RequestMapping("/score")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
public class ScoreController {

    private final ScoreService scoreService;
    private final ExcelImportService excelImportService;
    private final DataScopeService dataScopeService;

    @Operation(summary = "分页查询成绩")
    @GetMapping("/page")
    public Result<PageResult<ScoreVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) Boolean onlyFailed) {
        if (studentId != null) {
            dataScopeService.requireStudentAccess(studentId);
        }
        ScoreQuery query = new ScoreQuery();
        query.setStudentId(studentId);
        query.setCourseId(courseId);
        query.setTermId(termId);
        query.setOnlyFailed(onlyFailed);
        return Result.success(PageResult.of(
                scoreService.pageScores(current, size, query, dataScopeService.scopeForQuery())));
    }

    @Operation(summary = "Excel 批量导入成绩")
    @PostMapping("/import")
    public Result<ImportTask> importScores(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(excelImportService.importScores(file));
    }

    @Operation(summary = "下载成绩导入模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("成绩导入模板", StandardCharsets.UTF_8).replace("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), ScoreExcel.class)
                .sheet("成绩")
                .doWrite(List.of());
    }
}
