package com.gyb.xqfx.service;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.CourseCheckin;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.CourseCheckinMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 课堂签到服务测试：发起 / 输码签到 / 去重 / 结束 / 统计
 */
@DisplayName("课堂签到服务测试")
class CheckinServiceTest {

    private CourseCheckinMapper checkinMapper;
    private CourseOfferingMapper offeringMapper;
    private StudentMapper studentMapper;
    private AttendanceMapper attendanceMapper;
    private RoleService roleService;
    private CourseSpaceService courseSpaceService;
    private CheckinService service;

    @BeforeEach
    void setUp() {
        checkinMapper = Mockito.mock(CourseCheckinMapper.class);
        offeringMapper = Mockito.mock(CourseOfferingMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        attendanceMapper = Mockito.mock(AttendanceMapper.class);
        roleService = Mockito.mock(RoleService.class);
        courseSpaceService = Mockito.mock(CourseSpaceService.class);
        service = new CheckinService(checkinMapper, offeringMapper, studentMapper, attendanceMapper,
                roleService, courseSpaceService);
    }

    private CourseOffering offeringOf(Long id, Long clazzId) {
        CourseOffering o = new CourseOffering();
        o.setId(id);
        o.setTermId(1L);
        o.setClazzId(clazzId);
        return o;
    }

    private Student studentOf(Long id) {
        Student s = new Student();
        s.setId(id);
        s.setClazzId(2L);
        s.setUserId(100L);
        return s;
    }

    private CourseCheckin checkinOf(Long id, String code, String status) {
        CourseCheckin c = new CourseCheckin();
        c.setId(id);
        c.setCourseOfferingId(4L);
        c.setCode(code);
        c.setStatus(status);
        return c;
    }

    @Test
    @DisplayName("教师发起签到：生成 6 位数字码并通知班级")
    void create_checkin() {
        when(offeringMapper.selectById(4L)).thenReturn(offeringOf(4L, 2L));
        when(checkinMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { CourseCheckin cc = inv.getArgument(0); cc.setId(99L); return 1; })
                .when(checkinMapper).insert(any(CourseCheckin.class));

        CourseCheckin c = service.create(4L, 5L, "第1次签到");

        assertNotNull(c.getId() == null ? null : c.getId());
        assertEquals("ACTIVE", c.getStatus());
        assertTrue(c.getCode().matches("\\d{6}"), "签到码应为 6 位数字");
        verify(courseSpaceService).notifyOffering(eq(4L), any(), any(), eq("CHECKIN"), any());
    }

    @Test
    @DisplayName("学生输码签到：写入考勤 PRESENT")
    void sign_ok() {
        when(roleService.studentIdOf(100L)).thenReturn(1L);
        when(studentMapper.selectById(1L)).thenReturn(studentOf(1L));
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "ACTIVE"));
        when(offeringMapper.selectById(4L)).thenReturn(offeringOf(4L, 2L));
        when(courseSpaceService.studentInOffering(1L, 4L)).thenReturn(true);
        when(attendanceMapper.selectCount(any())).thenReturn(0L);
        final Attendance[] captured = new Attendance[1];
        doAnswer(inv -> { captured[0] = inv.getArgument(0); return 1; })
                .when(attendanceMapper).insert((Attendance) any());

        service.sign(100L, 2L, "058382");

        Attendance a = captured[0];
        assertNotNull(a);
        assertEquals(1L, a.getStudentId());
        assertEquals(4L, a.getCourseOfferingId());
        assertEquals("PRESENT", a.getStatus());
        assertEquals(LocalDate.now(), a.getCheckDate());
        assertEquals("CHECKIN:2", a.getRemark());
    }

    @Test
    @DisplayName("签到码错误被拒绝")
    void sign_wrong_code() {
        when(roleService.studentIdOf(100L)).thenReturn(1L);
        when(studentMapper.selectById(1L)).thenReturn(studentOf(1L));
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "ACTIVE"));
        when(offeringMapper.selectById(4L)).thenReturn(offeringOf(4L, 2L));
        when(courseSpaceService.studentInOffering(1L, 4L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.sign(100L, 2L, "000000"));
    }

    @Test
    @DisplayName("签到已结束被拒绝")
    void sign_closed() {
        when(roleService.studentIdOf(100L)).thenReturn(1L);
        when(studentMapper.selectById(1L)).thenReturn(studentOf(1L));
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "CLOSED"));

        assertThrows(BusinessException.class, () -> service.sign(100L, 2L, "058382"));
    }

    @Test
    @DisplayName("非本教学班学生被拒绝")
    void sign_not_enrolled() {
        when(roleService.studentIdOf(100L)).thenReturn(1L);
        when(studentMapper.selectById(1L)).thenReturn(studentOf(1L));
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "ACTIVE"));
        when(offeringMapper.selectById(4L)).thenReturn(offeringOf(4L, 2L));
        when(courseSpaceService.studentInOffering(1L, 4L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.sign(100L, 2L, "058382"));
    }

    @Test
    @DisplayName("当天已签到（去重）被拒绝")
    void sign_duplicate() {
        when(roleService.studentIdOf(100L)).thenReturn(1L);
        when(studentMapper.selectById(1L)).thenReturn(studentOf(1L));
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "ACTIVE"));
        when(offeringMapper.selectById(4L)).thenReturn(offeringOf(4L, 2L));
        when(courseSpaceService.studentInOffering(1L, 4L)).thenReturn(true);
        when(attendanceMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.sign(100L, 2L, "058382"));
    }

    @Test
    @DisplayName("结束签到置为 CLOSED")
    void close_checkin() {
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "ACTIVE"));
        service.close(2L, 5L);
        verify(checkinMapper).updateById(any(CourseCheckin.class));
    }

    @Test
    @DisplayName("签到统计：应到/已到/未到")
    void stats() {
        when(checkinMapper.selectById(2L)).thenReturn(checkinOf(2L, "058382", "ACTIVE"));
        when(offeringMapper.selectById(4L)).thenReturn(offeringOf(4L, 2L));
        Student s1 = studentOf(1L);
        Student s2 = studentOf(2L);
        when(courseSpaceService.offeringStudents(4L)).thenReturn(List.of(s1, s2));
        Attendance a1 = new Attendance();
        a1.setStudentId(1L);
        when(attendanceMapper.selectList(any())).thenReturn(List.of(a1));

        Map<String, Object> result = service.stats(2L);

        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("signedCount"));
        assertEquals(1, ((List<?>) result.get("signedList")).size());
        assertEquals(1, ((List<?>) result.get("absentList")).size());
    }
}
