package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.CourseChapter;
import com.gyb.xqfx.entity.CourseDiscussion;
import com.gyb.xqfx.entity.CourseDiscussionReply;
import com.gyb.xqfx.entity.CourseMaterial;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.CourseSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 课程空间（学习通装载第一批）：教师与学生共用
 *
 * <p>教师管理所授课程的章节/资料/讨论，学生进入所在班级课程学习。</p>
 */
@Tag(name = "课程空间")
@RestController
@RequiredArgsConstructor
@RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
public class CourseSpaceController {

    private final CourseSpaceService courseSpaceService;

    @Value("${xqfx.course.upload-dir:./upload/course}")
    private String uploadDir;

    @Operation(summary = "我的课程（教师=所授教学班；学生=所在班级课程）")
    @GetMapping("/course-space/my-courses")
    public Result<List<Map<String, Object>>> myCourses() {
        return Result.success(courseSpaceService.myCourses(
                SecurityUtils.currentUserId(), SecurityUtils.currentUserType()));
    }

    // ---------- 章节 ----------

    @Operation(summary = "课程章节列表")
    @GetMapping("/course-space/{offeringId}/chapters")
    public Result<List<Map<String, Object>>> chapters(@PathVariable Long offeringId) {
        return Result.success(courseSpaceService.chapters(offeringId));
    }

    @Operation(summary = "新建章节")
    @PostMapping("/course-space/{offeringId}/chapters")
    public Result<CourseChapter> createChapter(@PathVariable Long offeringId,
                                               @RequestParam(value = "chapterNo", required = false) Integer chapterNo,
                                               @RequestParam("title") String title,
                                               @RequestParam(value = "description", required = false) String description) {
        return Result.success(courseSpaceService.createChapter(offeringId, chapterNo, title, description));
    }

    @Operation(summary = "编辑章节")
    @PutMapping("/course-space/chapters/{id}")
    public Result<Void> updateChapter(@PathVariable Long id,
                                      @RequestParam(value = "title", required = false) String title,
                                      @RequestParam(value = "description", required = false) String description,
                                      @RequestParam(value = "chapterNo", required = false) Integer chapterNo) {
        courseSpaceService.updateChapter(id, title, description, chapterNo);
        return Result.success(null);
    }

    @Operation(summary = "删除章节")
    @DeleteMapping("/course-space/chapters/{id}")
    public Result<Void> deleteChapter(@PathVariable Long id) {
        courseSpaceService.deleteChapter(id);
        return Result.success(null);
    }

    // ---------- 资料 ----------

    @Operation(summary = "课程资料列表")
    @GetMapping("/course-space/{offeringId}/materials")
    public Result<List<Map<String, Object>>> materials(@PathVariable Long offeringId,
                                                       @RequestParam(value = "chapterId", required = false) Long chapterId) {
        return Result.success(courseSpaceService.materials(offeringId, chapterId));
    }

    @Operation(summary = "上传课程资料")
    @PostMapping("/course-space/{offeringId}/materials")
    public Result<CourseMaterial> uploadMaterial(@PathVariable Long offeringId,
                                                 @RequestParam(value = "chapterId", required = false) Long chapterId,
                                                 @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的资料文件");
        }
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        File dir = new File(uploadDir, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建上传目录失败");
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.toPath().resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败：" + e.getMessage());
        }
        String type = switch (ext) {
            case ".pdf" -> "PDF";
            case ".doc", ".docx" -> "WORD";
            case ".ppt", ".pptx" -> "PPT";
            case ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp" -> "IMAGE";
            case ".mp4", ".avi", ".mov", ".mkv" -> "VIDEO";
            default -> "OTHER";
        };
        CourseMaterial m = courseSpaceService.addMaterial(offeringId, chapterId, original, type,
                file.getSize(), target.toString(), SecurityUtils.currentUserId());
        return Result.success(m);
    }

    @Operation(summary = "下载课程资料")
    @GetMapping("/course-space/materials/{id}/file")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable Long id) {
        CourseMaterial material = courseSpaceService.findMaterial(id);
        if (material == null || material.getFilePath() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资料不存在");
        }
        File file = new File(material.getFilePath());
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件已丢失");
        }
        Resource resource = new FileSystemResource(file);
        String encoded = URLEncoder.encode(material.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "删除课程资料")
    @DeleteMapping("/course-space/materials/{id}")
    public Result<Void> deleteMaterial(@PathVariable Long id) {
        courseSpaceService.deleteMaterial(id);
        return Result.success(null);
    }

    // ---------- 讨论区 ----------

    @Operation(summary = "课程讨论列表（置顶优先）")
    @GetMapping("/course-space/{offeringId}/discussions")
    public Result<List<Map<String, Object>>> discussions(@PathVariable Long offeringId) {
        return Result.success(courseSpaceService.discussions(offeringId));
    }

    @Operation(summary = "发布讨论")
    @PostMapping("/course-space/{offeringId}/discussions")
    public Result<CourseDiscussion> createDiscussion(@PathVariable Long offeringId, @RequestParam("content") String content) {
        return Result.success(courseSpaceService.createDiscussion(offeringId, SecurityUtils.currentUserId(), content));
    }

    @Operation(summary = "回复讨论")
    @PostMapping("/course-space/discussions/{id}/reply")
    public Result<CourseDiscussionReply> reply(@PathVariable Long id, @RequestParam("content") String content) {
        return Result.success(courseSpaceService.replyDiscussion(id, SecurityUtils.currentUserId(), content));
    }

    @Operation(summary = "点赞讨论")
    @PostMapping("/course-space/discussions/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        courseSpaceService.likeDiscussion(id);
        return Result.success(null);
    }

    @Operation(summary = "置顶/取消置顶（教师）")
    @PostMapping("/course-space/discussions/{id}/pin")
    public Result<Void> pin(@PathVariable Long id, @RequestParam(value = "pinned", defaultValue = "1") Integer pinned) {
        if (!SecurityUtils.hasAnyRole("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅教师可置顶讨论");
        }
        courseSpaceService.pinDiscussion(id, pinned);
        return Result.success(null);
    }
}
