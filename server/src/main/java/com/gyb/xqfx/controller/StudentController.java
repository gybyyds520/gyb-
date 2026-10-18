package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.ImportTask;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.model.excel.StudentExcel;
import com.gyb.xqfx.model.query.StudentQuery;
import com.gyb.xqfx.model.vo.StudentVO;
import com.gyb.xqfx.service.ExcelImportService;
import com.gyb.xqfx.service.StudentService;
import com.gyb.xqfx.util.MaskUtils;
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
 * 学生管理
 */
@Tag(name = "学情数据-学生")
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final ExcelImportService excelImportService;

    @Operation(summary = "分页查询学生")
    @GetMapping("/page")
    public Result<PageResult<StudentVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long clazzId,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean masked) {
        StudentQuery query = new StudentQuery();
        query.setKeyword(keyword);
        query.setCollegeId(collegeId);
        query.setMajorId(majorId);
        query.setClazzId(clazzId);
        query.setGrade(grade);
        query.setStatus(status);
        return Result.success(PageResult.of(studentService.pageStudents(current, size, query, masked)));
    }

    @Operation(summary = "根据ID查询学生")
    @GetMapping("/{id}")
    public Result<StudentVO> getById(@PathVariable Long id) {
        Student s = studentService.getById(id);
        if (s == null) {
            return Result.fail("学生不存在");
        }
        StudentVO vo = new StudentVO();
        org.springframework.beans.BeanUtils.copyProperties(s, vo);
        vo.setPhoneMasked(MaskUtils.maskPhone(s.getPhone()));
        return Result.success(vo);
    }

    @Operation(summary = "新增学生")
    @PostMapping
    public Result<Boolean> save(@RequestBody Student student) {
        return Result.success(studentService.save(student));
    }

    @Operation(summary = "修改学生")
    @PutMapping
    public Result<Boolean> update(@RequestBody Student student) {
        return Result.success(studentService.updateById(student));
    }

    @Operation(summary = "删除学生")
    @DeleteMapping("/{id}")
    public Result<Boolean> remove(@PathVariable Long id) {
        return Result.success(studentService.removeById(id));
    }

    @Operation(summary = "Excel 批量导入学生")
    @PostMapping("/import")
    public Result<ImportTask> importStudents(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(excelImportService.importStudents(file));
    }

    @Operation(summary = "下载学生导入模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("学生导入模板", StandardCharsets.UTF_8).replace("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), StudentExcel.class)
                .sheet("学生")
                .doWrite(List.of());
    }
}
