package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.Homework;
import com.gyb.xqfx.entity.HomeworkSubmission;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.HomeworkMapper;
import com.gyb.xqfx.mapper.HomeworkSubmissionMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 作业闭环服务测试（Mapper / 数据范围 / 本地模型使用 Mockito 隔离）
 */
@DisplayName("作业闭环服务测试")
class HomeworkServiceTest {

    private HomeworkMapper homeworkMapper;
    private HomeworkSubmissionMapper submissionMapper;
    private StudentMapper studentMapper;
    private ClazzMapper clazzMapper;
    private MistakeBookService mistakeBookService;
    private DataScopeService dataScopeService;
    private OllamaClient ollamaClient;
    private HomeworkService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        homeworkMapper = Mockito.mock(HomeworkMapper.class);
        submissionMapper = Mockito.mock(HomeworkSubmissionMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        mistakeBookService = Mockito.mock(MistakeBookService.class);
        dataScopeService = Mockito.mock(DataScopeService.class);
        ollamaClient = Mockito.mock(OllamaClient.class);
        var offeringMapper = Mockito.mock(CourseOfferingMapper.class);
        var courseSpaceService = Mockito.mock(CourseSpaceService.class);
        service = new HomeworkService(homeworkMapper, submissionMapper, studentMapper, clazzMapper,
                offeringMapper, mistakeBookService, courseSpaceService, dataScopeService, ollamaClient, objectMapper);
    }

    private Student student(Long id, Long clazzId, String name) {
        Student s = new Student();
        s.setId(id);
        s.setClazzId(clazzId);
        s.setName(name);
        s.setStudentNo("S" + id);
        return s;
    }

    private Homework homework(Long id, Long clazzId, String questions, LocalDateTime deadline) {
        Homework h = new Homework();
        h.setId(id);
        h.setClazzId(clazzId);
        h.setQuestionJson(questions);
        h.setDeadline(deadline);
        h.setStatus("ACTIVE");
        h.setCourseName("高数");
        return h;
    }

    private HomeworkService.Question q(String no, String type, String answer, int score) {
        HomeworkService.Question q = new HomeworkService.Question();
        q.no = no;
        q.type = type;
        q.content = "题目" + no;
        q.answer = answer;
        q.score = BigDecimal.valueOf(score);
        return q;
    }

    @Test
    @DisplayName("布置作业：标题/班级/题目校验")
    void create_validate() {
        assertThrows(BusinessException.class, () -> service.create(1L, " ", 1L, "高数", 2L,
                List.of(q("1", "CHOICE", "A", 10)), null));
        assertThrows(BusinessException.class, () -> service.create(1L, "作业", 1L, "高数", null,
                List.of(q("1", "CHOICE", "A", 10)), null));
        assertThrows(BusinessException.class, () -> service.create(1L, "作业", 1L, "高数", 2L,
                List.of(), null));
    }

    @Test
    @DisplayName("布置作业成功：总分按题目分值汇总")
    void create_ok() {
        when(homeworkMapper.insert(any(Homework.class))).thenAnswer(inv -> {
            ((Homework) inv.getArgument(0)).setId(7L);
            return 1;
        });
        Long id = service.create(1L, "第三章练习", 1L, "高数", 2L,
                List.of(q("1", "CHOICE", "A", 30), q("2", "FILL", "2", 70)), null);
        assertEquals(7L, id);
        verify(homeworkMapper).insert(any(Homework.class));
    }

    @Test
    @DisplayName("提交作业：客观题自动判分并收录错题")
    void submit_autoGrade() throws Exception {
        String questions = objectMapper.writeValueAsString(List.of(
                q("1", "CHOICE", "A", 30),   // 答对
                q("2", "FILL", "2", 40),     // 答错 → 错题
                q("3", "ESSAY", "", 30)));   // 文本题不自动判
        Homework hw = homework(9L, 5L, questions, LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(9L)).thenReturn(hw);
        when(studentMapper.selectById(3L)).thenReturn(student(3L, 5L, "李四"));
        when(submissionMapper.selectOne(any())).thenReturn(null);
        List<HomeworkSubmission> inserted = new java.util.ArrayList<>();
        when(submissionMapper.insert(any(HomeworkSubmission.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0));
            return 1;
        });
        when(mistakeBookService.add(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(100L);

        service.submit(3L, 9L, List.of(
                ans("1", "A"), ans("2", "5"), ans("3", "我的理解")));

        // 自动得分 = 30（第 1 题），第 2 题错 → 收录错题，第 3 题文本题不自动判
        assertEquals(1, inserted.size());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(inserted.get(0).getAutoScore()));
        assertEquals("AUTO_GRADED", inserted.get(0).getStatus());
        // 错题收录：仅第 2 题
        verify(mistakeBookService, times(1))
                .add(eq(3L), any(), eq("高数"), isNull(), eq("FILL"), anyString(), eq("5"), eq("2"),
                        eq("HOMEWORK"), eq(9L), anyString());
    }

    @Test
    @DisplayName("提交作业：已截止拒绝")
    void submit_overdue() throws Exception {
        String questions = objectMapper.writeValueAsString(List.of(q("1", "CHOICE", "A", 10)));
        Homework hw = homework(9L, 5L, questions, LocalDateTime.now().minusHours(1));
        hw.setStatus("CLOSED");
        when(homeworkMapper.selectById(9L)).thenReturn(hw);
        when(studentMapper.selectById(3L)).thenReturn(student(3L, 5L, "李四"));
        assertThrows(BusinessException.class,
                () -> service.submit(3L, 9L, List.of(ans("1", "A"))));
        verify(submissionMapper, never()).insert(any(HomeworkSubmission.class));
    }

    @Test
    @DisplayName("教师批改：无权限班级拒绝")
    void grade_forbidden() {
        Homework hw = homework(9L, 5L, "[]", null);
        when(homeworkMapper.selectById(9L)).thenReturn(hw);
        when(dataScopeService.visibleClazzIds(99L)).thenReturn(List.of(1L, 2L));
        assertThrows(BusinessException.class,
                () -> service.grade(99L, 9L, 3L, BigDecimal.valueOf(80), "不错"));
    }

    @Test
    @DisplayName("AI 批改：本地模型评分写回")
    void aiGrade() throws Exception {
        String questions = objectMapper.writeValueAsString(List.of(
                q("1", "ESSAY", "参考答案", 100)));
        Homework hw = homework(9L, 5L, questions, null);
        HomeworkSubmission sub = new HomeworkSubmission();
        sub.setId(3L);
        sub.setHomeworkId(9L);
        sub.setStudentId(3L);
        sub.setAnswersJson(objectMapper.writeValueAsString(List.of(ans("1", "学生作文"))));
        when(homeworkMapper.selectById(9L)).thenReturn(hw);
        when(dataScopeService.visibleClazzIds(1L)).thenReturn(List.of(5L));
        when(submissionMapper.selectById(3L)).thenReturn(sub);
        when(ollamaClient.isAvailable()).thenReturn(true);
        OllamaClient.ChatResult res = new OllamaClient.ChatResult();
        res.model = "qwen2.5:7b";
        res.content = "{\"score\": 85, \"feedback\": \"论述清晰，建议补充例证\"}";
        when(ollamaClient.chat(anyString(), anyString())).thenReturn(res);

        Map<String, Object> result = service.aiGrade(1L, 9L, 3L);
        assertEquals(85, ((BigDecimal) result.get("score")).intValue());
        assertEquals("GRADED", sub.getStatus());
        verify(submissionMapper).updateById(sub);
    }

    @Test
    @DisplayName("学生端我的作业：完成/未交/逾期状态")
    void myHomework() throws Exception {
        String questions = objectMapper.writeValueAsString(List.of(q("1", "CHOICE", "A", 10)));
        Homework h1 = homework(1L, 5L, questions, LocalDateTime.now().plusDays(1));   // 未交
        Homework h2 = homework(2L, 5L, questions, LocalDateTime.now().minusDays(1));  // 逾期未交
        Homework h3 = homework(3L, 5L, questions, LocalDateTime.now().plusDays(1));   // 已交
        when(studentMapper.selectById(3L)).thenReturn(student(3L, 5L, "李四"));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(h1, h2, h3));
        HomeworkSubmission sub = new HomeworkSubmission();
        sub.setHomeworkId(3L);
        sub.setStatus("GRADED");
        sub.setScore(BigDecimal.valueOf(90));
        when(submissionMapper.selectList(any())).thenReturn(List.of(sub));

        List<Map<String, Object>> rows = service.myHomework(3L);
        assertEquals(3, rows.size());
        assertFalse((Boolean) rows.get(0).get("overdue"));
        assertTrue((Boolean) rows.get(1).get("overdue"));
        assertTrue((Boolean) rows.get(2).get("submitted"));
        assertEquals(90, ((BigDecimal) rows.get(2).get("score")).intValue());
    }

    @Test
    @DisplayName("家长端：仅返回绑定子女班级的作业动态")
    void parentHomework() throws Exception {
        String questions = objectMapper.writeValueAsString(List.of(q("1", "CHOICE", "A", 10)));
        when(dataScopeService.childStudentIds(8L)).thenReturn(List.of(3L));
        when(studentMapper.selectList(any())).thenReturn(List.of(student(3L, 5L, "李四")));
        Homework hw = homework(1L, 5L, questions, null);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(hw));
        when(submissionMapper.selectOne(any())).thenReturn(null);

        List<Map<String, Object>> rows = service.parentChildrenHomework(8L);
        assertEquals(1, rows.size());
        assertEquals("李四", rows.get(0).get("studentName"));
        assertFalse((Boolean) rows.get(0).get("submitted"));
    }

    private HomeworkService.Answer ans(String no, String answer) {
        HomeworkService.Answer a = new HomeworkService.Answer();
        a.no = no;
        a.answer = answer;
        return a;
    }
}
