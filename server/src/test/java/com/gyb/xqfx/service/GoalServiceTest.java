package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.LearningGoal;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.LearningGoalMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.model.vo.BiasVO;
import com.gyb.xqfx.model.vo.GoalVO;
import com.gyb.xqfx.model.vo.ResourceRecommendVO;
import com.gyb.xqfx.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 学习目标服务测试（登录上下文 + Mapper Mockito 隔离）
 */
@DisplayName("学习目标服务测试")
class GoalServiceTest {

    private LearningGoalMapper goalMapper;
    private StudentMapper studentMapper;
    private ScoreMapper scoreMapper;
    private CourseMapper courseMapper;
    private AcademicIndicatorService indicatorService;
    private ProfileService profileService;
    private GoalService service;

    @BeforeEach
    void setUp() {
        goalMapper = Mockito.mock(LearningGoalMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        courseMapper = Mockito.mock(CourseMapper.class);
        indicatorService = Mockito.mock(AcademicIndicatorService.class);
        profileService = Mockito.mock(ProfileService.class);
        service = new GoalService(goalMapper, studentMapper, scoreMapper, courseMapper,
                indicatorService, profileService, Mockito.mock(DataScopeService.class));

        // 模拟学生登录上下文：studentId=1
        Authentication auth = Mockito.mock(Authentication.class);
        LoginUser user = new LoginUser(7L, "stu", List.of())
                .setStudentId(1L);
        when(auth.getPrincipal()).thenReturn(user);
        SecurityContext ctx = Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Score score(Long courseId, BigDecimal total) {
        Score s = new Score();
        s.setStudentId(1L);
        s.setCourseId(courseId);
        s.setTotalScore(total);
        return s;
    }

    @Test
    @DisplayName("学生保存目标：自动绑定本人并默认进行中")
    void testSaveBindsStudent() {
        LearningGoal goal = new LearningGoal();
        goal.setGoalType("AVG_SCORE");
        goal.setTargetValue(new BigDecimal("85"));
        // 模拟 MyBatis-Plus insert 回填主键
        when(goalMapper.insert(any(LearningGoal.class))).thenAnswer(inv -> {
            LearningGoal g = inv.getArgument(0);
            g.setId(10L);
            return 1;
        });

        Long id = service.save(null, goal);
        assertEquals(10L, id);
        assertEquals(1L, goal.getStudentId());
        assertEquals("ACTIVE", goal.getStatus());
    }

    @Test
    @DisplayName("保存目标缺类型或目标值应拒绝")
    void testSaveValidation() {
        LearningGoal goal = new LearningGoal();
        goal.setGoalType("GPA");
        assertThrows(com.gyb.xqfx.common.BusinessException.class, () -> service.save(null, goal));
    }

    @Test
    @DisplayName("目标列表：均分目标实时计算当前值与进度")
    void testListAvgScoreGoal() {
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");
        when(studentMapper.selectById(1L)).thenReturn(student);
        when(scoreMapper.selectList(any())).thenReturn(List.of(
                score(100L, new BigDecimal("80")),
                score(200L, new BigDecimal("70"))));
        when(courseMapper.selectList(any())).thenReturn(List.of());
        AcademicIndicatorVO ind = new AcademicIndicatorVO();
        ind.setGpa(new BigDecimal("3.0"));
        when(indicatorService.calculate(1L, null)).thenReturn(ind);

        LearningGoal g = new LearningGoal();
        g.setId(10L);
        g.setStudentId(1L);
        g.setGoalType("AVG_SCORE");
        g.setTargetValue(new BigDecimal("80"));
        g.setStatus("ACTIVE");
        when(goalMapper.selectList(any())).thenReturn(List.of(g));

        List<GoalVO> list = service.list(null, null);
        assertEquals(1, list.size());
        assertEquals(0, new BigDecimal("75.00").compareTo(list.get(0).getCurrentValue()));
        assertEquals(0, new BigDecimal("5.00").compareTo(list.get(0).getGap()));
        assertEquals(94, list.get(0).getProgress());
        assertEquals("张三", list.get(0).getStudentName());
    }

    @Test
    @DisplayName("弱科驱动推荐：生成三类资源且优先级排序")
    void testRecommendationsFromWeakCourse() {
        when(goalMapper.selectList(any())).thenReturn(List.of());

        BiasVO bias = new BiasVO();
        bias.setStudentName("张三");
        bias.setBaseline("CLAZZ");
        BiasVO.BiasItem weak = new BiasVO.BiasItem();
        weak.setCourseId(100L);
        weak.setCourseName("高等数学");
        weak.setCourseType("MAJOR");
        weak.setStudentAvg(new BigDecimal("55"));
        weak.setBaselineAvg(new BigDecimal("70"));
        weak.setDiff(new BigDecimal("-15.00"));
        weak.setRankPercent(10);
        weak.setVerdict("WEAK");
        bias.setItems(List.of(weak));
        when(profileService.biasAnalysis(1L, null)).thenReturn(bias);

        List<ResourceRecommendVO> recs = service.recommendations(null, null);
        assertEquals(3, recs.size());
        assertEquals("VIDEO", recs.get(0).getResourceType());
        assertEquals("PRACTICE", recs.get(1).getResourceType());
        assertEquals("COACH", recs.get(2).getResourceType());
        assertTrue(recs.get(0).getPriority() < recs.get(1).getPriority());
        assertEquals(100L, recs.get(0).getCourseId());
        assertEquals("高等数学", recs.get(0).getCourseName());
        assertTrue(recs.get(0).getTitle().contains("高等数学"));
    }
}
