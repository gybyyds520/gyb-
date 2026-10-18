package com.gyb.xqfx.service;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 课程空间服务测试：我的课程 / 章节 / 讨论 / 资料
 */
@DisplayName("课程空间服务测试")
class CourseSpaceServiceTest {

    private CourseOfferingMapper offeringMapper;
    private CourseMapper courseMapper;
    private TeacherMapper teacherMapper;
    private StudentMapper studentMapper;
    private ClazzMapper clazzMapper;
    private TermMapper termMapper;
    private CourseChapterMapper chapterMapper;
    private CourseMaterialMapper materialMapper;
    private CourseDiscussionMapper discussionMapper;
    private CourseDiscussionReplyMapper replyMapper;
    private CourseEnrollmentMapper enrollmentMapper;
    private NotifyMessageMapper notifyMapper;
    private RoleService roleService;
    private CourseSpaceService service;

    @BeforeEach
    void setUp() {
        offeringMapper = Mockito.mock(CourseOfferingMapper.class);
        courseMapper = Mockito.mock(CourseMapper.class);
        teacherMapper = Mockito.mock(TeacherMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        termMapper = Mockito.mock(TermMapper.class);
        chapterMapper = Mockito.mock(CourseChapterMapper.class);
        materialMapper = Mockito.mock(CourseMaterialMapper.class);
        discussionMapper = Mockito.mock(CourseDiscussionMapper.class);
        replyMapper = Mockito.mock(CourseDiscussionReplyMapper.class);
        enrollmentMapper = Mockito.mock(CourseEnrollmentMapper.class);
        notifyMapper = Mockito.mock(NotifyMessageMapper.class);
        roleService = Mockito.mock(RoleService.class);
        service = new CourseSpaceService(offeringMapper, courseMapper, teacherMapper, studentMapper,
                clazzMapper, termMapper, chapterMapper, materialMapper, discussionMapper,
                replyMapper, enrollmentMapper, notifyMapper, roleService);
    }

    @Test
    @DisplayName("学生我的课程：选课关系优先（跨班选修）")
    void myCourses_student_by_enrollment() {
        when(roleService.studentIdOf(100L)).thenReturn(1L);
        Student s = new Student();
        s.setId(1L);
        s.setClazzId(2L);
        when(studentMapper.selectById(1L)).thenReturn(s);
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseOfferingId(4L);
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(e));
        when(offeringMapper.selectList(any())).thenReturn(List.of());
        CourseOffering o = new CourseOffering();
        o.setId(4L);
        o.setCourseId(1L);
        o.setTeacherId(4L);
        when(offeringMapper.selectBatchIds(any())).thenReturn(List.of(o));
        Course c = new Course();
        c.setCourseName("程序设计基础");
        when(courseMapper.selectById(1L)).thenReturn(c);
        Teacher t = new Teacher();
        t.setName("王磊");
        when(teacherMapper.selectById(4L)).thenReturn(t);

        List<Map<String, Object>> result = service.myCourses(100L, "STUDENT");

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).get("offeringId"));
        assertEquals("程序设计基础", result.get(0).get("courseName"));
    }

    @Test
    @DisplayName("教师我的课程：按任课教师查询")
    void myCourses_teacher() {
        when(roleService.teacherIdOf(5L)).thenReturn(4L);
        CourseOffering o = new CourseOffering();
        o.setId(4L);
        o.setCourseId(1L);
        o.setTeacherId(4L);
        o.setClazzId(2L);
        when(offeringMapper.selectList(any())).thenReturn(List.of(o));
        Course c = new Course();
        c.setCourseName("程序设计基础");
        when(courseMapper.selectById(1L)).thenReturn(c);
        Teacher t = new Teacher();
        t.setName("王磊");
        when(teacherMapper.selectById(4L)).thenReturn(t);
        Clazz cl = new Clazz();
        cl.setClazzName("计科一班");
        when(clazzMapper.selectById(2L)).thenReturn(cl);

        List<Map<String, Object>> result = service.myCourses(5L, "TEACHER");

        assertEquals(1, result.size());
        assertEquals("计科一班", result.get(0).get("clazzName"));
    }

    @Test
    @DisplayName("新建章节：自动序号 + 标题校验")
    void create_chapter() {
        when(chapterMapper.selectList(any())).thenReturn(List.of());
        CourseChapter c = service.createChapter(4L, null, "第一章", null);
        assertEquals(1, c.getChapterNo());
        assertEquals("第一章", c.getTitle());
        verify(chapterMapper).insert((CourseChapter) any());
        assertThrows(BusinessException.class, () -> service.createChapter(4L, null, "  ", null));
    }

    @Test
    @DisplayName("章节列表：含资料计数")
    void chapters_with_material_count() {
        CourseChapter c = new CourseChapter();
        c.setId(10L);
        c.setChapterNo(1);
        c.setTitle("第一章");
        when(chapterMapper.selectList(any())).thenReturn(List.of(c));
        when(materialMapper.selectCount(any())).thenReturn(3L);

        List<Map<String, Object>> result = service.chapters(4L);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).get("materialCount"));
    }

    @Test
    @DisplayName("发布讨论：作者名与类型解析（教师）")
    void create_discussion_teacher() {
        Teacher t = new Teacher();
        t.setName("王磊");
        when(teacherMapper.selectOne(any())).thenReturn(t);
        when(discussionMapper.insert(any(CourseDiscussion.class))).thenReturn(1);

        CourseDiscussion d = service.createDiscussion(4L, 5L, "大家好");

        assertEquals("王磊", d.getAuthorName());
        assertEquals("TEACHER", d.getAuthorType());
        assertThrows(BusinessException.class, () -> service.createDiscussion(4L, 5L, "   "));
    }

    @Test
    @DisplayName("回复与点赞：计数更新")
    void reply_and_like() {
        CourseDiscussion d = new CourseDiscussion();
        d.setId(9L);
        d.setReplyCount(0);
        d.setLikeCount(0);
        when(discussionMapper.selectById(9L)).thenReturn(d);
        when(replyMapper.insert(any(CourseDiscussionReply.class))).thenReturn(1);
        when(discussionMapper.updateById(any(CourseDiscussion.class))).thenReturn(1);

        service.replyDiscussion(9L, 100L, "老师好");
        assertEquals(1, d.getReplyCount());
        service.likeDiscussion(9L);
        assertEquals(1, d.getLikeCount());
    }

    @Test
    @DisplayName("置顶讨论")
    void pin_discussion() {
        CourseDiscussion d = new CourseDiscussion();
        d.setId(9L);
        when(discussionMapper.selectById(9L)).thenReturn(d);
        service.pinDiscussion(9L, 1);
        assertEquals(1, d.getPinned());
    }
}
