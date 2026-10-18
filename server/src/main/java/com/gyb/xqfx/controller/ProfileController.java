package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.ProfileSnapshot;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.TagDefinition;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TagDefinitionMapper;
import com.gyb.xqfx.model.vo.BiasVO;
import com.gyb.xqfx.model.vo.ProfileVO;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.AiAnalysisService;
import com.gyb.xqfx.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 学生画像与标签
 */
@Tag(name = "学生画像")
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final com.gyb.xqfx.service.DataScopeService dataScopeService;
    private final StudentMapper studentMapper;
    private final TagDefinitionMapper tagDefinitionMapper;
    private final AiAnalysisService aiAnalysisService;

    @Operation(summary = "查询学生画像（无快照时自动生成）")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @GetMapping("/student")
    public Result<ProfileVO> profile(@RequestParam Long studentId,
                                     @RequestParam(required = false) Long termId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(profileService.getProfile(studentId, termId));
    }

    @Operation(summary = "生成画像快照")
    @PostMapping("/generate")
    @RequireRole({"ADMIN"})
    public Result<ProfileSnapshot> generate(@RequestParam Long studentId,
                                            @RequestParam(required = false) Long termId) {
        return Result.success(profileService.generate(studentId, termId), "画像生成成功");
    }

    @Operation(summary = "画像快照历史（成长档案时间轴数据源）")
    @GetMapping("/snapshots")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ProfileSnapshot>> snapshots(@RequestParam Long studentId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(profileService.snapshots(studentId));
    }

    @Operation(summary = "偏科分析（学生各课程强弱项对比）")
    @GetMapping("/bias")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<BiasVO> bias(@RequestParam Long studentId,
                               @RequestParam(required = false) Long termId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(profileService.biasAnalysis(studentId, termId));
    }

    @Operation(summary = "偏科大模型归因（强弱项成因解读）")
    @PostMapping("/bias/attribution")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AiAnalysisService.AiResult> biasAttribution(
            @RequestParam Long studentId,
            @RequestParam(required = false) Long termId) {
        dataScopeService.requireStudentAccess(studentId);
        BiasVO bias = profileService.biasAnalysis(studentId, termId);
        AiAnalysisService.StudentContext ctx = aiAnalysisService.loadContext(studentId, termId);
        StringBuilder sb = new StringBuilder();
        sb.append("【偏科分析数据】\n学生：").append(bias.getStudentName())
                .append("\n对比基准：").append(bias.getBaseline())
                .append("\n强科数：").append(bias.getStrongCount())
                .append("，弱科数：").append(bias.getWeakCount())
                .append("，偏科指数：").append(bias.getBiasIndex()).append("\n");
        if (bias.getItems() != null) {
            for (BiasVO.BiasItem it : bias.getItems()) {
                sb.append("- 课程：").append(it.getCourseName())
                        .append("，类型：").append(it.getCourseType())
                        .append("，学生均分：").append(it.getStudentAvg())
                        .append("，基准均分：").append(it.getBaselineAvg())
                        .append("，差值：").append(it.getDiff())
                        .append("，百分位：").append(it.getRankPercent())
                        .append("%，判定：").append(it.getVerdict())
                        .append("，现有建议：").append(it.getAdvice()).append("\n");
            }
        }
        sb.append("\n【该生学情上下文】\n").append(ctx.toText());
        return Result.success(aiAnalysisService.generateAttribution(
                "BIAS_ATTRIBUTION", sb.toString(), studentId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "批量生成全体在读学生画像")
    @PostMapping("/batch")
    @RequireRole({"ADMIN"})
    public Result<Map<String, Object>> batch(@RequestParam(required = false) Long termId) {
        List<Student> students = studentMapper.selectList(null);
        int success = 0;
        int failed = 0;
        for (Student s : students) {
            try {
                profileService.generate(s.getId(), termId);
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", students.size());
        result.put("success", success);
        result.put("failed", failed);
        return Result.success(result, "批量生成完成");
    }
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})

    @Operation(summary = "标签定义列表")
    @GetMapping("/tags")
    public Result<List<TagDefinition>> tags() {
        return Result.success(tagDefinitionMapper.selectList(null));
    }

    @Operation(summary = "新增或更新标签定义")
    @PostMapping("/tags")
    @RequireRole({"ADMIN"})
    public Result<Boolean> saveTag(@RequestBody TagDefinition tag) {
        return Result.success(tag.getId() == null
                ? tagDefinitionMapper.insert(tag) > 0
                : tagDefinitionMapper.updateById(tag) > 0);
    }
}
