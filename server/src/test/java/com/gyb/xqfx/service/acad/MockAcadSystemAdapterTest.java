package com.gyb.xqfx.service.acad;

import com.gyb.xqfx.service.DemoDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 教务 Mock 适配器测试：验证同步结果计数与类型映射
 */
@DisplayName("教务 Mock 适配器测试")
class MockAcadSystemAdapterTest {

    private Map<String, Object> summary() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("student", 50);
        s.put("course", 12);
        s.put("score", 480);
        s.put("attendance", 900);
        return s;
    }

    @Test
    @DisplayName("同步学生返回教务推送计数")
    void testSyncStudents() {
        DemoDataService demo = Mockito.mock(DemoDataService.class);
        when(demo.generate(anyInt())).thenReturn(summary());
        MockAcadSystemAdapter adapter = new MockAcadSystemAdapter(demo);

        assertEquals("mock", adapter.providerName());
        AcadSyncResult r = adapter.syncStudents();
        assertEquals("STUDENTS", r.getItemType());
        assertEquals(50, r.getSuccessCount());
        assertEquals(0, r.getFailCount());
        assertTrue(r.getMessage().contains("同步成功"));
    }

    @Test
    @DisplayName("同步课程与成绩映射正确")
    void testSyncCoursesAndScores() {
        DemoDataService demo = Mockito.mock(DemoDataService.class);
        when(demo.generate(anyInt())).thenReturn(summary());
        MockAcadSystemAdapter adapter = new MockAcadSystemAdapter(demo);

        AcadSyncResult courses = adapter.syncCourses();
        assertEquals(12, courses.getSuccessCount());
        assertEquals("COURSES", courses.getItemType());

        AcadSyncResult scores = adapter.syncScores();
        assertEquals(480, scores.getSuccessCount());
        assertEquals("SCORES", scores.getItemType());
    }

    @Test
    @DisplayName("同步考勤与失败结果结构")
    void testSyncAttendanceAndFail() {
        DemoDataService demo = Mockito.mock(DemoDataService.class);
        when(demo.generate(anyInt())).thenReturn(summary());
        MockAcadSystemAdapter adapter = new MockAcadSystemAdapter(demo);

        AcadSyncResult attendance = adapter.syncAttendance();
        assertEquals(900, attendance.getSuccessCount());

        AcadSyncResult fail = AcadSyncResult.fail("mock", "STUDENTS", "教务系统不可达");
        assertEquals("教务系统不可达", fail.getMessage());
        assertEquals(0, fail.getFailCount());
        assertNotNull(fail.getProvider());
    }
}
