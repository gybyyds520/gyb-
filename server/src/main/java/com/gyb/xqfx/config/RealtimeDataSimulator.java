package com.gyb.xqfx.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * 运行中实时数据模拟器：按固定间隔模拟"新考勤 / 新在线行为 / 新预警"流入系统，
 * 使大屏、预警、画像等页面在运行期间持续呈现实时变化。
 * <p>
 * 通过 {@code xqfx.realtime.enabled} 关闭；间隔与单轮条数可配置。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeDataSimulator {

    private static final String[] PERIODS = {"1-2节", "3-4节", "5-6节", "7-8节", "9-10节"};
    private static final String[] ATTEND_STATUS = {"PRESENT", "PRESENT", "PRESENT", "PRESENT", "PRESENT",
            "PRESENT", "PRESENT", "LATE", "LATE", "LEAVE", "ABSENT", "EARLY"};
    private static final String[] WARNING_TYPES = {"ACADEMIC", "ACADEMIC", "ACADEMIC", "ATTENDANCE",
            "HOMEWORK", "PROGRESS", "BEHAVIOR"};
    private static final String[] WARNING_TITLES = {
            "成绩波动预警", "考勤异常预警", "作业完成率下降预警", "学习进度滞后预警",
            "课堂表现下滑预警", "在线学习时长不足预警"};
    private static final String[] WARNING_CONTENTS = {
            "近期成绩出现明显下滑，建议关注该生学习状态",
            "本周出现多次迟到/缺勤，建议及时沟通了解原因",
            "作业提交及时率低于班级平均水平，存在欠交风险",
            "课程学习进度落后于教学计划，需加强课后学习",
            "课堂参与度与表现较前几周下降",
            "在线学习时长低于预警阈值，学习投入不足"};

    private final StudentMapper studentMapper;
    private final TermMapper termMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final AttendanceMapper attendanceMapper;
    private final OnlineBehaviorMapper onlineBehaviorMapper;
    private final WarningRecordMapper warningRecordMapper;

    @Value("${xqfx.realtime.enabled:true}")
    private boolean enabled;

    @Value("${xqfx.realtime.batch:5}")
    private int batch;

    private final Random random = new Random();

    @Scheduled(fixedDelayString = "${xqfx.realtime.interval-ms:60000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            List<Student> students = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().last("order by rand() limit " + batch));
            if (students.isEmpty()) {
                return;
            }
            Term term = currentTerm();
            List<CourseOffering> offerings = courseOfferingMapper.selectList(
                    new LambdaQueryWrapper<CourseOffering>().last("limit 200"));

            int attendance = 0;
            int behavior = 0;
            int warnings = 0;
            LocalDate today = LocalDate.now();
            for (Student s : students) {
                // 1. 新考勤
                Attendance at = new Attendance();
                at.setStudentId(s.getId());
                at.setCourseOfferingId(offerings.isEmpty() ? null
                        : offerings.get(random.nextInt(offerings.size())).getId());
                at.setTermId(term == null ? null : term.getId());
                at.setCheckDate(today);
                at.setPeriod(PERIODS[random.nextInt(PERIODS.length)]);
                at.setStatus(ATTEND_STATUS[random.nextInt(ATTEND_STATUS.length)]);
                attendanceMapper.insert(at);
                attendance++;

                // 2. 新在线行为
                OnlineBehavior ob = new OnlineBehavior();
                ob.setStudentId(s.getId());
                ob.setCourseId(offerings.isEmpty() ? null
                        : offerings.get(random.nextInt(offerings.size())).getCourseId());
                ob.setTermId(term == null ? null : term.getId());
                ob.setStatDate(today);
                ob.setLoginCount(1 + random.nextInt(5));
                ob.setStudyMinutes(10 + random.nextInt(120));
                ob.setVideoProgress(new BigDecimal(30 + random.nextInt(70)));
                ob.setResourceViewCount(random.nextInt(20));
                ob.setForumPostCount(random.nextInt(3));
                ob.setAssignmentSubmitCount(random.nextInt(3));
                onlineBehaviorMapper.insert(ob);
                behavior++;

                // 3. 新预警（约 60% 概率触发，保证动态但不过量）
                if (random.nextInt(100) < 60) {
                    WarningRecord w = new WarningRecord();
                    w.setWarningNo("RT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                            + random.nextInt(1000));
                    w.setStudentId(s.getId());
                    w.setTermId(term == null ? null : term.getId());
                    w.setWarningLevel(1 + random.nextInt(4));
                    w.setWarningType(WARNING_TYPES[random.nextInt(WARNING_TYPES.length)]);
                    w.setTitle(WARNING_TITLES[random.nextInt(WARNING_TITLES.length)]);
                    w.setContent(WARNING_CONTENTS[random.nextInt(WARNING_CONTENTS.length)]);
                    w.setRiskProbability(new BigDecimal("0.3").add(new BigDecimal(random.nextInt(66))
                            .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP)));
                    w.setTriggerDetail("实时数据模拟器触发（演示）");
                    w.setTriggerValue(w.getRiskProbability().multiply(BigDecimal.valueOf(100)));
                    w.setStatus("ACTIVE");
                    warningRecordMapper.insert(w);
                    warnings++;
                }
            }
            log.info("实时数据流模拟完成：考勤 {} 条，行为 {} 条，新预警 {} 条", attendance, behavior, warnings);
        } catch (Exception e) {
            log.warn("实时数据模拟失败（不影响系统运行）：{}", e.getMessage());
        }
    }

    private Term currentTerm() {
        Term t = termMapper.selectOne(new LambdaQueryWrapper<Term>().eq(Term::getIsCurrent, 1).last("limit 1"));
        if (t != null) {
            return t;
        }
        return termMapper.selectOne(new LambdaQueryWrapper<Term>().orderByDesc(Term::getId).last("limit 1"));
    }
}
