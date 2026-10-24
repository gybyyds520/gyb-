package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.client.SdServiceClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.DataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 成长激励卡（AI 生成）
 *
 * <p>链路：读取学生学情亮点（最高分课程/平均绩点/趋势）→ 组装提示词 →
 * 本地 Stable Diffusion（sd-turbo）生成激励卡背景图 → 存服务器 → 前端展示下载。
 * 学生本人、家长（绑定孩子）、教师/辅导员/管理员（授权范围内学生）可用。</p>
 */
@Slf4j
@Tag(name = "成长激励卡")
@RestController
@RequiredArgsConstructor
@RequestMapping("/motivation")
public class MotivationController {

    private final SdServiceClient sdServiceClient;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final DataScopeService dataScopeService;

    @Value("${xqfx.motivation.upload-dir:./upload/motivation}")
    private String uploadDir;

    /** 风格模板：背景描述（SD 只画图，文字由前端叠加） */
    private static final Map<String, String> STYLES = Map.of(
            "ACADEMIC", "蓝白配色科技感扁平插画，翻开的书本与上升的阶梯，晨光与远山，简约大气，干净背景",
            "STAR", "深蓝夜空与金色星轨，闪闪发光的奖杯与流星，励志唯美，扁平插画风格，干净背景",
            "NATURE", "清新绿色自然插画，破土而出的树苗与向阳花，柔和阳光，温馨治愈，扁平插画，干净背景"
    );

    @Operation(summary = "生成成长激励卡（AI 绘图）")
    @PostMapping("/generate")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Map<String, Object>> generate(@RequestParam(value = "style", defaultValue = "ACADEMIC") String style,
                                                @RequestParam(value = "studentId", required = false) Long studentId) {
        String styleName = STYLES.containsKey(style) ? style : "ACADEMIC";
        Long targetStudentId = resolveTargetStudent(studentId);
        Student student = studentMapper.selectById(targetStudentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }

        // 1. 学情亮点
        Map<String, Object> highlight = buildHighlight(targetStudentId);
        // 2. 组装提示词
        String prompt = STYLES.get(styleName) + "，" + highlight.get("promptWord");
        // 3. 本地 SD 生成
        byte[] png = sdServiceClient.generate(prompt, 512, 512);
        // 4. 落盘
        String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + ".png";
        File dir = new File(uploadDir, month);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "激励卡目录创建失败");
        }
        Path target = Paths.get(dir.getAbsolutePath(), fileName);
        try {
            Files.write(target, png);
        } catch (IOException e) {
            log.error("激励卡图片写入失败", e);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "激励卡保存失败");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("imageUrl", "/api/motivation/image/" + month + "/" + fileName);
        data.put("prompt", prompt);
        data.put("studentName", student.getName());
        data.put("style", styleName);
        data.put("highlight", highlight);
        return Result.success(data, "生成成功，可在本地环境预览下载");
    }

    @Operation(summary = "读取激励卡图片（放行）")
    @GetMapping("/image/{month}/{fileName}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String month,
                                                    @PathVariable String fileName) {
        // 防目录穿越
        if (!month.matches("\\d{6}") || !fileName.matches("[0-9a-f]{32}\\.png")) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(uploadDir, month + File.separator + fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }

    /** 解析目标学生：学生本人=自己；家长/教师传 studentId 时做数据范围校验 */
    private Long resolveTargetStudent(Long studentId) {
        String userType = SecurityUtils.currentUserType();
        if (studentId != null) {
            dataScopeService.requireStudentAccess(studentId);
            return studentId;
        }
        if ("STUDENT".equals(userType)) {
            Long sid = SecurityUtils.currentStudentId();
            if (sid == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定学生档案");
            }
            return sid;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请指定要生成激励卡的学生（studentId）");
    }

    /** 学情亮点：最高分课程 + 平均绩点 + 亮点词 */
    private Map<String, Object> buildHighlight(Long studentId) {
        List<Score> best = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(Score::getIsPass, 1)
                .orderByDesc(Score::getTotalScore)
                .last("limit 1"));
        List<BigDecimal> gps = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .eq(Score::getStudentId, studentId)
                        .isNotNull(Score::getGradePoint)
                        .select(Score::getGradePoint))
                .stream()
                .map(Score::getGradePoint)
                .toList();
        BigDecimal avgGp = gps.isEmpty()
                ? BigDecimal.ZERO
                : gps.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(gps.size()), 2, BigDecimal.ROUND_HALF_UP);

        Map<String, Object> highlight = new HashMap<>();
        if (!best.isEmpty()) {
            highlight.put("bestCourseId", best.get(0).getCourseId());
            highlight.put("bestScore", best.get(0).getTotalScore());
            highlight.put("promptWord", "闪耀的进步之光，向上的力量，充满希望与成就感");
        } else {
            highlight.put("promptWord", "努力的种子正在发芽，坚持终有回报，充满希望");
        }
        highlight.put("avgGpa", avgGp);
        return highlight;
    }
}
