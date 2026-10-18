package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.College;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Major;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CollegeMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import com.gyb.xqfx.mapper.TermMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 基础数据：学院 / 专业 / 班级 / 学期 / 课程 / 教师 / 开课
 */
@Tag(name = "基础数据")
@RestController
@RequestMapping("/base")
@RequiredArgsConstructor
public class BaseDataController {

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final TermMapper termMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final CourseOfferingMapper courseOfferingMapper;

    private static final List<String> TYPES =
            List.of("college", "major", "clazz", "term", "course", "teacher", "offering");

    @Operation(summary = "查询列表")
    @GetMapping("/{type}/list")
    public Result<List<?>> list(@PathVariable String type) {
        return Result.success(mapper(type).selectList(null));
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{type}/{id}")
    public Result<?> get(@PathVariable String type, @PathVariable Long id) {
        return Result.success(mapper(type).selectById(id));
    }

    @Operation(summary = "新增或更新")
    @PostMapping("/{type}")
    public Result<Boolean> save(@PathVariable String type, @RequestBody java.util.Map<String, Object> body) {
        Object entity = toEntity(type, body);
        @SuppressWarnings("rawtypes")
        BaseMapper mapper = mapper(type);
        Object id = body.get("id");
        int rows = (id == null) ? mapper.insert(entity) : mapper.updateById(entity);
        return Result.success(rows > 0);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{type}/{id}")
    public Result<Boolean> remove(@PathVariable String type, @PathVariable Long id) {
        return Result.success(mapper(type).deleteById(id) > 0);
    }

    // ---------- 私有辅助 ----------

    @SuppressWarnings("rawtypes")
    private BaseMapper mapper(String type) {
        return switch (type) {
            case "college" -> collegeMapper;
            case "major" -> majorMapper;
            case "clazz" -> clazzMapper;
            case "term" -> termMapper;
            case "course" -> courseMapper;
            case "teacher" -> teacherMapper;
            case "offering" -> courseOfferingMapper;
            default -> throw new BusinessException("不支持的基础数据类型：" + type + "，可选：" + TYPES);
        };
    }

    @SuppressWarnings("unchecked")
    private Object toEntity(String type, java.util.Map<String, Object> body) {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        Class<?> clazz = switch (type) {
            case "college" -> College.class;
            case "major" -> Major.class;
            case "clazz" -> Clazz.class;
            case "term" -> Term.class;
            case "course" -> Course.class;
            case "teacher" -> Teacher.class;
            case "offering" -> CourseOffering.class;
            default -> throw new BusinessException("不支持的基础数据类型：" + type);
        };
        try {
            return om.convertValue(body, clazz);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("基础数据参数转换失败：" + e.getMessage());
        }
    }
}
