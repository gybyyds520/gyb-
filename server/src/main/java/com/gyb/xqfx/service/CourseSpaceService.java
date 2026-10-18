package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程空间（学习通装载第一批）
 *
 * <p>教师：所授教学班 → 章节 / 资料 / 讨论 / 测验 / 签到聚合管理；
 * 学生：所在班级的课程 → 章节任务 / 资料 / 讨论 / 测验 / 签到。</p>
 */
@Service
@RequiredArgsConstructor
public class CourseSpaceService {

    private final CourseOfferingMapper offeringMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final TermMapper termMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseMaterialMapper materialMapper;
    private final CourseDiscussionMapper discussionMapper;
    private final CourseDiscussionReplyMapper replyMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final NotifyMessageMapper notifyMapper;
    private final RoleService roleService;

    // ---------- 我的课程 ----------

    public List<Map<String, Object>> myCourses(Long userId, String userType) {
        List<CourseOffering> offerings;
        if (userType == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限");
        }
        switch (userType) {
            case "ADMIN", "SCHOOL_ADMIN" -> offerings = offeringMapper.selectList(
                    new LambdaQueryWrapper<CourseOffering>().orderByDesc(CourseOffering::getId));
            case "COLLEGE_ADMIN", "COUNSELOR", "TEACHER" -> {
                Long teacherId = roleService.teacherIdOf(userId);
                if (teacherId == null) {
                    offerings = List.of();
                } else {
                    offerings = offeringMapper.selectList(new LambdaQueryWrapper<CourseOffering>()
                            .eq(CourseOffering::getTeacherId, teacherId));
                }
            }
            case "STUDENT" -> {
                Long studentId = roleService.studentIdOf(userId);
                Student student = studentId == null ? null : studentMapper.selectById(studentId);
                if (student == null) {
                    offerings = List.of();
                } else {
                    // 选课关系 ∪ 行政班匹配（跨班选修与行政班并存）
                    List<Long> enrolledIds = enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                                    .eq(CourseEnrollment::getStudentId, studentId))
                            .stream().map(CourseEnrollment::getCourseOfferingId).toList();
                    List<Long> clazzIds = student.getClazzId() == null ? List.of()
                            : offeringMapper.selectList(new LambdaQueryWrapper<CourseOffering>()
                                    .eq(CourseOffering::getClazzId, student.getClazzId()))
                            .stream().map(CourseOffering::getId).toList();
                    java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
                    ids.addAll(enrolledIds);
                    ids.addAll(clazzIds);
                    offerings = ids.isEmpty() ? List.of() : offeringMapper.selectBatchIds(ids);
                }
            }
            default -> offerings = List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseOffering o : offerings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", o.getId());
            row.put("courseId", o.getCourseId());
            row.put("termId", o.getTermId());
            row.put("classroom", o.getClassroom());
            row.put("schedule", o.getSchedule());
            row.put("capacity", o.getCapacity());
            Course course = o.getCourseId() == null ? null : courseMapper.selectById(o.getCourseId());
            row.put("courseName", course == null ? "" : course.getCourseName());
            row.put("courseCode", course == null ? "" : course.getCourseCode());
            Teacher teacher = o.getTeacherId() == null ? null : teacherMapper.selectById(o.getTeacherId());
            row.put("teacherName", teacher == null ? "" : teacher.getName());
            Clazz clazz = o.getClazzId() == null ? null : clazzMapper.selectById(o.getClazzId());
            row.put("clazzName", clazz == null ? "" : clazz.getClazzName());
            Term term = o.getTermId() == null ? null : termMapper.selectById(o.getTermId());
            row.put("termName", term == null ? "" : term.getTermName());
            result.add(row);
        }
        return result;
    }

    // ---------- 章节 ----------

    public List<Map<String, Object>> chapters(Long offeringId) {
        List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseOfferingId, offeringId)
                .orderByAsc(CourseChapter::getChapterNo));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseChapter c : chapters) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("chapterNo", c.getChapterNo());
            row.put("title", c.getTitle());
            row.put("description", c.getDescription());
            long materialCount = materialMapper.selectCount(new LambdaQueryWrapper<CourseMaterial>()
                    .eq(CourseMaterial::getChapterId, c.getId()));
            row.put("materialCount", materialCount);
            result.add(row);
        }
        return result;
    }

    public CourseChapter createChapter(Long offeringId, Integer chapterNo, String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入章节标题");
        }
        CourseChapter c = new CourseChapter();
        c.setCourseOfferingId(offeringId);
        c.setChapterNo(chapterNo == null ? nextChapterNo(offeringId) : chapterNo);
        c.setTitle(title);
        c.setDescription(description);
        chapterMapper.insert(c);
        return c;
    }

    private int nextChapterNo(Long offeringId) {
        int max = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseOfferingId, offeringId))
                .stream().mapToInt(CourseChapter::getChapterNo).max().orElse(0);
        return max + 1;
    }

    public void updateChapter(Long chapterId, String title, String description, Integer chapterNo) {
        CourseChapter c = chapterMapper.selectById(chapterId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "章节不存在");
        }
        if (title != null && !title.isBlank()) {
            c.setTitle(title);
        }
        if (description != null) {
            c.setDescription(description);
        }
        if (chapterNo != null) {
            c.setChapterNo(chapterNo);
        }
        chapterMapper.updateById(c);
    }

    public void deleteChapter(Long chapterId) {
        chapterMapper.deleteById(chapterId);
    }

    // ---------- 资料 ----------

    public CourseMaterial addMaterial(Long offeringId, Long chapterId, String fileName, String fileType,
                                      Long fileSize, String filePath, Long uploaderId) {
        CourseMaterial m = new CourseMaterial();
        m.setCourseOfferingId(offeringId);
        m.setChapterId(chapterId);
        m.setFileName(fileName);
        m.setFileType(fileType);
        m.setFileSize(fileSize);
        m.setFilePath(filePath);
        m.setUploaderId(uploaderId);
        materialMapper.insert(m);
        return m;
    }

    public List<Map<String, Object>> materials(Long offeringId, Long chapterId) {
        LambdaQueryWrapper<CourseMaterial> qw = new LambdaQueryWrapper<CourseMaterial>()
                .eq(CourseMaterial::getCourseOfferingId, offeringId)
                .orderByDesc(CourseMaterial::getId);
        if (chapterId != null) {
            qw.eq(CourseMaterial::getChapterId, chapterId);
        }
        List<CourseMaterial> list = materialMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseMaterial m : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("chapterId", m.getChapterId());
            row.put("fileName", m.getFileName());
            row.put("fileType", m.getFileType());
            row.put("fileSize", m.getFileSize());
            row.put("filePath", m.getFilePath());
            row.put("createTime", m.getCreateTime());
            result.add(row);
        }
        return result;
    }

    public void deleteMaterial(Long materialId) {
        materialMapper.deleteById(materialId);
    }

    public CourseMaterial findMaterial(Long materialId) {
        return materialMapper.selectById(materialId);
    }

    // ---------- 讨论区 ----------

    public List<Map<String, Object>> discussions(Long offeringId) {
        List<CourseDiscussion> list = discussionMapper.selectList(new LambdaQueryWrapper<CourseDiscussion>()
                .eq(CourseDiscussion::getCourseOfferingId, offeringId)
                .orderByDesc(CourseDiscussion::getPinned)
                .orderByDesc(CourseDiscussion::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseDiscussion d : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("authorId", d.getAuthorId());
            row.put("authorName", d.getAuthorName());
            row.put("authorType", d.getAuthorType());
            row.put("content", d.getContent());
            row.put("pinned", d.getPinned());
            row.put("likeCount", d.getLikeCount());
            row.put("replyCount", d.getReplyCount());
            row.put("createTime", d.getCreateTime());
            row.put("replies", repliesOf(d.getId()));
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> repliesOf(Long discussionId) {
        List<CourseDiscussionReply> list = replyMapper.selectList(new LambdaQueryWrapper<CourseDiscussionReply>()
                .eq(CourseDiscussionReply::getDiscussionId, discussionId)
                .orderByAsc(CourseDiscussionReply::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseDiscussionReply r : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("authorId", r.getAuthorId());
            row.put("authorName", r.getAuthorName());
            row.put("authorType", r.getAuthorType());
            row.put("content", r.getContent());
            row.put("createTime", r.getCreateTime());
            result.add(row);
        }
        return result;
    }

    public CourseDiscussion createDiscussion(Long offeringId, Long userId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入讨论内容");
        }
        CourseDiscussion d = new CourseDiscussion();
        d.setCourseOfferingId(offeringId);
        d.setAuthorId(userId);
        d.setAuthorName(resolveUserName(userId));
        d.setAuthorType(resolveUserType(userId));
        d.setContent(content.trim());
        discussionMapper.insert(d);
        return d;
    }

    public CourseDiscussionReply replyDiscussion(Long discussionId, Long userId, String content) {
        CourseDiscussion d = discussionMapper.selectById(discussionId);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "讨论不存在");
        }
        CourseDiscussionReply r = new CourseDiscussionReply();
        r.setDiscussionId(discussionId);
        r.setAuthorId(userId);
        r.setAuthorName(resolveUserName(userId));
        r.setAuthorType(resolveUserType(userId));
        r.setContent(content == null ? "" : content.trim());
        replyMapper.insert(r);
        d.setReplyCount(d.getReplyCount() == null ? 1 : d.getReplyCount() + 1);
        discussionMapper.updateById(d);
        return r;
    }

    public void likeDiscussion(Long discussionId) {
        CourseDiscussion d = discussionMapper.selectById(discussionId);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "讨论不存在");
        }
        d.setLikeCount(d.getLikeCount() == null ? 1 : d.getLikeCount() + 1);
        discussionMapper.updateById(d);
    }

    public void pinDiscussion(Long discussionId, Integer pinned) {
        CourseDiscussion d = discussionMapper.selectById(discussionId);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "讨论不存在");
        }
        d.setPinned(pinned == null ? 1 : pinned);
        discussionMapper.updateById(d);
    }

    // ---------- 辅助 ----------

    private String resolveUserName(Long userId) {
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
        if (teacher != null) {
            return teacher.getName();
        }
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
        if (student != null) {
            return student.getName();
        }
        return "用户" + userId;
    }

    private String resolveUserType(Long userId) {
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
        return teacher != null ? "TEACHER" : "STUDENT";
    }

    /** 给某行政班全体学生发站内通知 */
    @Transactional
    public void notifyClazz(Long clazzId, String title, String content, String bizType, Long bizId) {
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>().eq(Student::getClazzId, clazzId));
        for (Student s : students) {
            if (s.getUserId() == null) {
                continue;
            }
            NotifyMessage msg = new NotifyMessage();
            msg.setUserId(s.getUserId());
            msg.setMessageType("SYSTEM");
            msg.setTitle(title);
            msg.setContent(content);
            msg.setBizType(bizType);
            msg.setBizId(bizId);
            notifyMapper.insert(msg);
        }
    }

    /** 教学班学生：选课关系优先，其次行政班 */
    public List<Student> offeringStudents(Long offeringId) {
        CourseOffering offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            return List.of();
        }
        List<Long> enrolledIds = enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseOfferingId, offeringId))
                .stream().map(CourseEnrollment::getStudentId).toList();
        if (!enrolledIds.isEmpty()) {
            return studentMapper.selectBatchIds(enrolledIds);
        }
        if (offering.getClazzId() != null) {
            return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .eq(Student::getClazzId, offering.getClazzId()));
        }
        return List.of();
    }

    /** 学生选课的教学班 ID 列表 */
    public List<Long> enrolledOfferingIds(Long studentId) {
        return enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, studentId))
                .stream().map(CourseEnrollment::getCourseOfferingId).toList();
    }

    /** 教师是否归属该教学班（跨班选修以 teacherId 判定） */
    public boolean teacherOwnsOffering(Long userId, Long offeringId) {
        CourseOffering offering = offeringMapper.selectById(offeringId);
        if (offering == null || offering.getTeacherId() == null) {
            return false;
        }
        Long teacherId = roleService.teacherIdOf(userId);
        return teacherId != null && teacherId.equals(offering.getTeacherId());
    }

    /** 学生是否属于该教学班 */
    public boolean studentInOffering(Long studentId, Long offeringId) {
        CourseOffering offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            return false;
        }
        Long cnt = enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseOfferingId, offeringId));
        if (cnt != null && cnt > 0) {
            return true;
        }
        Student s = studentMapper.selectById(studentId);
        return s != null && offering.getClazzId() != null && offering.getClazzId().equals(s.getClazzId());
    }

    /** 给教学班全体学生发站内通知（选课关系优先，其次行政班） */
    @Transactional
    public void notifyOffering(Long offeringId, String title, String content, String bizType, Long bizId) {
        for (Student s : offeringStudents(offeringId)) {
            if (s.getUserId() == null) {
                continue;
            }
            NotifyMessage msg = new NotifyMessage();
            msg.setUserId(s.getUserId());
            msg.setMessageType("SYSTEM");
            msg.setTitle(title);
            msg.setContent(content);
            msg.setBizType(bizType);
            msg.setBizId(bizId);
            notifyMapper.insert(msg);
        }
    }
}
