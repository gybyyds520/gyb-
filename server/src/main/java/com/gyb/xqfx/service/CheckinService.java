package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课堂签到（学习通装载第一批）
 *
 * <p>教师发起数字码签到 → 学生输码签到 → 写入考勤表 attendance(PRESENT) → 通知学生；
 * 与考勤预警指标打通。教师可结束签到、查看统计、补签。</p>
 */
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CourseCheckinMapper checkinMapper;
    private final CourseOfferingMapper offeringMapper;
    private final StudentMapper studentMapper;
    private final AttendanceMapper attendanceMapper;
    private final RoleService roleService;
    private final CourseSpaceService courseSpaceService;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 教师发起签到，返回签到活动（含签到码） */
    public CourseCheckin create(Long offeringId, Long teacherId, String title) {
        CourseOffering offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        String code = genUniqueCode();
        CourseCheckin c = new CourseCheckin();
        c.setCourseOfferingId(offeringId);
        c.setTeacherId(teacherId);
        c.setCode(code);
        c.setTitle(title == null || title.isBlank() ? "课堂签到" : title.trim());
        c.setStatus("ACTIVE");
        checkinMapper.insert(c);
        courseSpaceService.notifyOffering(offeringId, "签到提醒",
                "「" + c.getTitle() + "」已开始，请立即到课程空间签到（签到码 " + code + "）", "CHECKIN", c.getId());
        return c;
    }

    /** 学生签到：校验签到码 → 写入考勤（当天去重） */
    @Transactional
    public void sign(Long userId, Long checkinId, String code) {
        Long studentId = roleService.studentIdOf(userId);
        if (studentId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅学生可签到");
        }
        Student student = studentMapper.selectById(studentId);
        CourseCheckin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null || !"ACTIVE".equals(checkin.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "签到已结束");
        }
        if (checkin.getCode() == null || !checkin.getCode().equalsIgnoreCase(code == null ? "" : code.trim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "签到码错误");
        }
        CourseOffering offering = offeringMapper.selectById(checkin.getCourseOfferingId());
        if (offering == null || student == null
                || !courseSpaceService.studentInOffering(studentId, checkin.getCourseOfferingId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该签到不属于你的课程");
        }
        // 防重复：当天该教学班已有签到记录
        Long dup = attendanceMapper.selectCount(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getStudentId, studentId)
                .eq(Attendance::getCourseOfferingId, offering.getId())
                .eq(Attendance::getCheckDate, LocalDate.now())
                .eq(Attendance::getStatus, "PRESENT"));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "今天已完成签到");
        }
        Attendance a = new Attendance();
        a.setStudentId(studentId);
        a.setCourseOfferingId(offering.getId());
        a.setTermId(offering.getTermId());
        a.setCheckDate(LocalDate.now());
        a.setPeriod(null);
        a.setStatus("PRESENT");
        a.setRemark("CHECKIN:" + checkin.getId());
        attendanceMapper.insert(a);
    }

    /** 教师结束签到 */
    public void close(Long checkinId, Long teacherId) {
        CourseCheckin c = checkinMapper.selectById(checkinId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "签到不存在");
        }
        c.setStatus("CLOSED");
        c.setEndTime(LocalDateTime.now());
        checkinMapper.updateById(c);
    }

    /** 教师补签 */
    @Transactional
    public void teacherSign(Long checkinId, Long studentId, Long teacherId) {
        CourseCheckin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "签到不存在");
        }
        CourseOffering offering = offeringMapper.selectById(checkin.getCourseOfferingId());
        Student student = studentMapper.selectById(studentId);
        if (offering == null || student == null
                || !courseSpaceService.studentInOffering(studentId, checkin.getCourseOfferingId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该学生不属于本教学班");
        }
        Attendance a = new Attendance();
        a.setStudentId(studentId);
        a.setCourseOfferingId(offering.getId());
        a.setTermId(offering.getTermId());
        a.setCheckDate(LocalDate.now());
        a.setStatus("PRESENT");
        a.setRemark("CHECKIN:" + checkin.getId() + ":MANUAL");
        attendanceMapper.insert(a);
    }

    public List<Map<String, Object>> listByOffering(Long offeringId) {
        List<CourseCheckin> list = checkinMapper.selectList(new LambdaQueryWrapper<CourseCheckin>()
                .eq(CourseCheckin::getCourseOfferingId, offeringId)
                .orderByDesc(CourseCheckin::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseCheckin c : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("title", c.getTitle());
            row.put("code", c.getCode());
            row.put("status", c.getStatus());
            row.put("startTime", c.getStartTime());
            row.put("endTime", c.getEndTime());
            row.put("signedCount", signedCount(c.getId()));
            result.add(row);
        }
        return result;
    }

    /** 签到统计：应到/已到/未到名单 */
    public Map<String, Object> stats(Long checkinId) {
        CourseCheckin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "签到不存在");
        }
        CourseOffering offering = offeringMapper.selectById(checkin.getCourseOfferingId());
        List<Student> students = offering == null ? List.of() : courseSpaceService.offeringStudents(offering.getId());
        List<Attendance> signed = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getStatus, "PRESENT")
                .likeRight(Attendance::getRemark, "CHECKIN:" + checkinId)
                .eq(Attendance::getCheckDate, LocalDate.now()));
        List<Map<String, Object>> signedList = new ArrayList<>();
        List<Map<String, Object>> absentList = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.getId());
            row.put("studentNo", s.getStudentNo());
            row.put("name", s.getName());
            boolean in = signed.stream().anyMatch(a -> a.getStudentId().equals(s.getId()));
            row.put("signed", in);
            (in ? signedList : absentList).add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", students.size());
        result.put("signedCount", signedList.size());
        result.put("signedList", signedList);
        result.put("absentList", absentList);
        return result;
    }

    /** 学生视角：所属课程的待签/历史签到 */
    public List<Map<String, Object>> studentCheckins(Long userId) {
        Long studentId = roleService.studentIdOf(userId);
        if (studentId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅学生可查看");
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            return List.of();
        }
        // 选课关系 ∪ 行政班匹配
        List<Long> enrolledIds = courseSpaceService.enrolledOfferingIds(studentId);
        List<Long> clazzIds = student.getClazzId() == null ? List.of()
                : offeringMapper.selectList(new LambdaQueryWrapper<CourseOffering>()
                        .eq(CourseOffering::getClazzId, student.getClazzId()))
                .stream().map(CourseOffering::getId).toList();
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        ids.addAll(enrolledIds);
        ids.addAll(clazzIds);
        List<CourseOffering> offerings = ids.isEmpty() ? List.of() : offeringMapper.selectBatchIds(ids);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseOffering o : offerings) {
            List<CourseCheckin> list = checkinMapper.selectList(new LambdaQueryWrapper<CourseCheckin>()
                    .eq(CourseCheckin::getCourseOfferingId, o.getId())
                    .orderByDesc(CourseCheckin::getCreateTime));
            for (CourseCheckin c : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                row.put("offeringId", o.getId());
                row.put("title", c.getTitle());
                row.put("status", c.getStatus());
                row.put("startTime", c.getStartTime());
                row.put("signed", signedToday(studentId, o.getId()));
                result.add(row);
            }
        }
        return result;
    }

    private boolean signedToday(Long studentId, Long offeringId) {
        Long cnt = attendanceMapper.selectCount(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getStudentId, studentId)
                .eq(Attendance::getCourseOfferingId, offeringId)
                .eq(Attendance::getCheckDate, LocalDate.now())
                .eq(Attendance::getStatus, "PRESENT"));
        return cnt != null && cnt > 0;
    }

    private int signedCount(Long checkinId) {
        return attendanceMapper.selectCount(new LambdaQueryWrapper<Attendance>()
                .likeRight(Attendance::getRemark, "CHECKIN:" + checkinId)
                .eq(Attendance::getStatus, "PRESENT")
                .eq(Attendance::getCheckDate, LocalDate.now())).intValue();
    }

    private String genUniqueCode() {
        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (checkinMapper.selectCount(new LambdaQueryWrapper<CourseCheckin>()
                .eq(CourseCheckin::getCode, code)
                .eq(CourseCheckin::getStatus, "ACTIVE")) > 0);
        return code;
    }
}
