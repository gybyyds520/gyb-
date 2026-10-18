package com.gyb.xqfx.config;

import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 实时数据模拟器测试：单轮产生新考勤/新行为与新预警
 */
@DisplayName("实时数据模拟器测试")
class RealtimeDataSimulatorTest {

    private StudentMapper studentMapper;
    private TermMapper termMapper;
    private CourseOfferingMapper courseOfferingMapper;
    private AttendanceMapper attendanceMapper;
    private OnlineBehaviorMapper onlineBehaviorMapper;
    private WarningRecordMapper warningRecordMapper;
    private RealtimeDataSimulator simulator;

    @BeforeEach
    void setUp() {
        studentMapper = Mockito.mock(StudentMapper.class);
        termMapper = Mockito.mock(TermMapper.class);
        courseOfferingMapper = Mockito.mock(CourseOfferingMapper.class);
        attendanceMapper = Mockito.mock(AttendanceMapper.class);
        onlineBehaviorMapper = Mockito.mock(OnlineBehaviorMapper.class);
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        simulator = new RealtimeDataSimulator(studentMapper, termMapper, courseOfferingMapper,
                attendanceMapper, onlineBehaviorMapper, warningRecordMapper);
        ReflectionTestUtils.setField(simulator, "enabled", true);
        ReflectionTestUtils.setField(simulator, "batch", 2);
    }

    @Test
    @DisplayName("开关关闭时不产生数据")
    void disabled() {
        ReflectionTestUtils.setField(simulator, "enabled", false);
        simulator.tick();
        verify(attendanceMapper, never()).insert(any(com.gyb.xqfx.entity.Attendance.class));
        verify(onlineBehaviorMapper, never()).insert(any(com.gyb.xqfx.entity.OnlineBehavior.class));
        verify(warningRecordMapper, never()).insert(any(com.gyb.xqfx.entity.WarningRecord.class));
    }

    @Test
    @DisplayName("单轮为每个学生生成考勤与行为，并可能生成预警")
    void tickGeneratesData() {
        Student s1 = new Student();
        s1.setId(1L);
        Student s2 = new Student();
        s2.setId(2L);
        when(studentMapper.selectList(any())).thenReturn(List.of(s1, s2));
        Term term = new Term();
        term.setId(101L);
        when(termMapper.selectOne(any())).thenReturn(term);
        when(courseOfferingMapper.selectList(any())).thenReturn(List.of());

        simulator.tick();

        ArgumentCaptor<com.gyb.xqfx.entity.Attendance> atCap = ArgumentCaptor.forClass(com.gyb.xqfx.entity.Attendance.class);
        verify(attendanceMapper, times(2)).insert(atCap.capture());
        atCap.getAllValues().forEach(a -> {
            assertEquals(101L, a.getTermId());
            assertNotNull(a.getCheckDate());
            assertNotNull(a.getStatus());
        });

        ArgumentCaptor<com.gyb.xqfx.entity.OnlineBehavior> obCap = ArgumentCaptor.forClass(com.gyb.xqfx.entity.OnlineBehavior.class);
        verify(onlineBehaviorMapper, times(2)).insert(obCap.capture());
        obCap.getAllValues().forEach(o -> assertEquals(101L, o.getTermId()));

        // 60% 概率触发预警：2 名学生大概率至少 1 条，且均为 ACTIVE
        verify(warningRecordMapper, atMost(2)).insert(any(com.gyb.xqfx.entity.WarningRecord.class));
    }
}
