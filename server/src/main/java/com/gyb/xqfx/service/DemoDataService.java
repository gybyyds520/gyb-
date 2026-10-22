package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.College;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.CourseEnrollment;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Major;
import com.gyb.xqfx.entity.OnlineBehavior;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CollegeMapper;
import com.gyb.xqfx.mapper.CourseEnrollmentMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.OnlineBehaviorMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.service.impl.ScoreServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 演示数据生成器：一键生成学院/专业/班级/学期/课程/教师/学生/成绩/考勤/在线行为
 * 用于毕设演示与自动化测试，避免依赖真实校方数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private static final String[] SURNAMES = {"王", "李", "张", "刘", "陈", "杨", "黄", "赵", "周", "吴", "徐", "孙", "林", "何", "郭"};
    private static final String[] GIVEN = {"伟", "芳", "娜", "敏", "静", "磊", "洋", "勇", "艳", "杰", "涛", "明", "超", "秀英", "霞", "平", "刚", "桂英", "子涵", "浩然"};
    private static final String[] COURSE_NAMES = {
            "高等数学", "线性代数", "概率论与数理统计", "程序设计基础", "数据结构", "算法设计与分析",
            "操作系统", "计算机网络", "数据库原理", "软件工程", "机器学习导论", "人工智能基础"
    };
    private static final String[] TITLES = {"教授", "副教授", "讲师", "助教"};

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final TermMapper termMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final AttendanceMapper attendanceMapper;
    private final OnlineBehaviorMapper onlineBehaviorMapper;
    private final StudentService studentService;
    private final ScoreService scoreService;
    private final AttendanceService attendanceService;
    private final OnlineBehaviorService onlineBehaviorService;

    /**
     * 生成演示数据
     *
     * @param studentCount 学生数量
     * @return 各表生成条数统计
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(int studentCount) {
        Random random = new Random(20261017L);
        Map<String, Object> summary = new LinkedHashMap<>();

        // 1. 学院
        List<College> colleges = new ArrayList<>();
        String[][] collegeDef = {{"C01", "计算与信息科学学院"}, {"C02", "电子与电气工程学院"}, {"C03", "经济与管理学院"}};
        for (String[] def : collegeDef) {
            College c = collegeMapper.selectOne(new LambdaQueryWrapper<College>().eq(College::getCollegeCode, def[0]));
            if (c == null) {
                c = new College();
                c.setCollegeCode(def[0]);
                c.setCollegeName(def[1]);
                c.setDean("负责人" + def[0]);
                collegeMapper.insert(c);
            }
            colleges.add(c);
        }
        summary.put("college", colleges.size());

        // 2. 专业（每学院 2 个）
        List<Major> majors = new ArrayList<>();
        String[][] majorDef = {
                {"M0101", "计算机科学与技术", "C01"}, {"M0102", "软件工程", "C01"},
                {"M0201", "电子信息工程", "C02"}, {"M0202", "自动化", "C02"},
                {"M0301", "信息管理与信息系统", "C03"}, {"M0302", "电子商务", "C03"}
        };
        Map<String, Long> collegeIdByCode = new LinkedHashMap<>();
        colleges.forEach(c -> collegeIdByCode.put(c.getCollegeCode(), c.getId()));
        for (String[] def : majorDef) {
            Major m = majorMapper.selectOne(new LambdaQueryWrapper<Major>().eq(Major::getMajorCode, def[0]));
            if (m == null) {
                m = new Major();
                m.setMajorCode(def[0]);
                m.setMajorName(def[1]);
                m.setCollegeId(collegeIdByCode.get(def[2]));
                m.setDegreeType("工学学士");
                majorMapper.insert(m);
            }
            majors.add(m);
        }
        summary.put("major", majors.size());

        // 3. 班级（每专业 2 个）
        List<Clazz> clazzList = new ArrayList<>();
        String[] grades = {"2023", "2024", "2025"};
        int clazzSeq = 1;
        for (Major m : majors) {
            for (int k = 1; k <= 2; k++) {
                String code = m.getMajorCode() + "0" + k;
                Clazz cz = clazzMapper.selectOne(new LambdaQueryWrapper<Clazz>().eq(Clazz::getClazzCode, code));
                if (cz == null) {
                    cz = new Clazz();
                    cz.setClazzCode(code);
                    cz.setClazzName(m.getMajorName() + k + "班");
                    cz.setMajorId(m.getId());
                    cz.setGrade(grades[clazzSeq % grades.length]);
                    clazzMapper.insert(cz);
                }
                clazzList.add(cz);
                clazzSeq++;
            }
        }
        summary.put("clazz", clazzList.size());

        // 4. 学期
        List<Term> terms = new ArrayList<>();
        String[][] termDef = {{"2025-2026-2", "2025-2026学年第二学期", "2026-02-23", "2026-07-05", "0"},
                {"2026-2027-1", "2026-2027学年第一学期", "2026-08-31", "2027-01-16", "1"}};
        for (String[] def : termDef) {
            Term t = termMapper.selectOne(new LambdaQueryWrapper<Term>().eq(Term::getTermCode, def[0]));
            if (t == null) {
                t = new Term();
                t.setTermCode(def[0]);
                t.setTermName(def[1]);
                t.setStartDate(LocalDate.parse(def[2]));
                t.setEndDate(LocalDate.parse(def[3]));
                t.setIsCurrent(Integer.valueOf(def[4]));
                termMapper.insert(t);
            }
            terms.add(t);
        }
        summary.put("term", terms.size());

        // 5. 课程与教师
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < COURSE_NAMES.length; i++) {
            String code = String.format("CS%03d", i + 1);
            Course c = courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getCourseCode, code));
            if (c == null) {
                c = new Course();
                c.setCourseCode(code);
                c.setCourseName(COURSE_NAMES[i]);
                c.setCredit(new BigDecimal(i < 3 ? "4.0" : "3.0"));
                c.setCourseType(i < 3 ? "PUBLIC" : (i < 9 ? "CORE" : "ELECTIVE"));
                c.setCollegeId(colleges.get(0).getId());
                c.setTotalHours(i < 3 ? 64 : 48);
                courseMapper.insert(c);
            }
            courses.add(c);

            String tNo = String.format("T%03d", i + 1);
            Teacher tc = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getTeacherNo, tNo));
            if (tc == null) {
                tc = new Teacher();
                tc.setTeacherNo(tNo);
                tc.setName(SURNAMES[random.nextInt(SURNAMES.length)] + GIVEN[random.nextInt(GIVEN.length)]);
                tc.setGender(random.nextBoolean() ? "男" : "女");
                tc.setCollegeId(colleges.get(i % colleges.size()).getId());
                tc.setTitle(TITLES[random.nextInt(TITLES.length)]);
                tc.setDegree("硕士");
                tc.setResearchArea("软件工程、大模型应用开发");
                teacherMapper.insert(tc);
            }
        }
        summary.put("course", courses.size());

        // 6. 开课（学期 × 课程）
        Map<String, Long> offeringKeyToId = new LinkedHashMap<>();
        for (Term t : terms) {
            for (Course c : courses) {
                Long teacherId = teacherMapper.selectList(null).get(0).getId();
                CourseOffering co = new CourseOffering();
                co.setTermId(t.getId());
                co.setCourseId(c.getId());
                co.setTeacherId(teacherId);
                co.setCapacity(120);
                co.setClassroom("教学楼A-" + (100 + c.getId().intValue()));
                courseOfferingMapper.insert(co);
                offeringKeyToId.put(t.getId() + "#" + c.getId(), co.getId());
            }
        }
        summary.put("courseOffering", offeringKeyToId.size());

        // 7. 学生
        Map<Long, Long> collegeIdByMajorId = new LinkedHashMap<>();
        majors.forEach(m -> collegeIdByMajorId.put(m.getId(), m.getCollegeId()));
        int existing = studentMapper.selectCount(null).intValue();
        List<Student> students = new ArrayList<>();
        for (int i = 1; i <= studentCount; i++) {
            String studentNo = String.format("23%08d", existing + i);
            if (studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo)) > 0) {
                continue;
            }
            Clazz cz = clazzList.get(i % clazzList.size());
            Student s = new Student();
            s.setStudentNo(studentNo);
            s.setName(SURNAMES[random.nextInt(SURNAMES.length)] + GIVEN[random.nextInt(GIVEN.length)]);
            s.setGender(random.nextBoolean() ? "男" : "女");
            s.setClazzId(cz.getId());
            s.setMajorId(cz.getMajorId());
            s.setCollegeId(collegeIdByMajorId.get(cz.getMajorId()));
            s.setGrade(cz.getGrade());
            s.setEnrollDate(LocalDate.of(Integer.parseInt(cz.getGrade()), 9, 1));
            s.setPhone("138" + String.format("%08d", random.nextInt(100000000)));
            s.setStatus("ENROLLED");
            s.setPoliticalStatus(random.nextBoolean() ? "共青团员" : "群众");
            students.add(s);
        }
        if (!students.isEmpty()) {
            studentService.saveBatch(students, 500);
        }
        summary.put("student", students.size());

        // 重新加载全部学生
        List<Student> allStudents = studentMapper.selectList(null);
        Map<Long, Student> studentById = new LinkedHashMap<>();
        for (Student s : allStudents) {
            studentById.put(s.getId(), s);
        }

        // 8. 选课关系（每门开课约 70% 学生；已存在的选课保留）
        //    成绩只属于选课学生 → 普通教师可见范围收敛为自己的学生（数据范围隔离）
        Map<Long, List<Long>> enrolledByOffering = new LinkedHashMap<>();
        int enrollCreated = 0;
        for (Map.Entry<String, Long> e : offeringKeyToId.entrySet()) {
            Long offeringId = e.getValue();
            List<CourseEnrollment> exists = enrollmentMapper.selectList(
                    new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getCourseOfferingId, offeringId));
            if (!exists.isEmpty()) {
                enrolledByOffering.put(offeringId,
                        exists.stream().map(CourseEnrollment::getStudentId).toList());
                continue;
            }
            Collections.shuffle(allStudents, random);
            int take = Math.max(1, (int) Math.round(allStudents.size() * 0.7));
            List<Long> ids = new ArrayList<>();
            for (int k = 0; k < take && k < allStudents.size(); k++) {
                Student st = allStudents.get(k);
                CourseEnrollment ce = new CourseEnrollment();
                ce.setCourseOfferingId(offeringId);
                ce.setStudentId(st.getId());
                ce.setEnrollTime(LocalDateTime.now());
                enrollmentMapper.insert(ce);
                ids.add(st.getId());
                enrollCreated++;
            }
            enrolledByOffering.put(offeringId, ids);
        }
        summary.put("courseEnrollment", enrollCreated);

        // 9. 成绩（仅选课学生 × 开课；未选课学生无成绩 → 教师数据范围隔离生效）
        List<Score> scores = new ArrayList<>();
        for (Map.Entry<String, Long> e : offeringKeyToId.entrySet()) {
            String key = e.getKey();
            Long offeringId = e.getValue();
            String[] parts = key.split("#");
            Long termId = Long.parseLong(parts[0]);
            Long courseId = Long.parseLong(parts[1]);
            List<Long> enrolledIds = enrolledByOffering.getOrDefault(offeringId, List.of());
            for (Long studentId : enrolledIds) {
                Student s = studentById.get(studentId);
                if (s == null) {
                    continue;
                }
                double base = 62 + random.nextGaussian() * 12;   // 学生基础水平
                double v = base + random.nextGaussian() * 8 - (courseId % 3) * 2;
                v = Math.max(28, Math.min(99, v));
                BigDecimal total = new BigDecimal(v).setScale(1, RoundingMode.HALF_UP);
                Score sc = new Score();
                sc.setStudentId(s.getId());
                sc.setCourseId(courseId);
                sc.setTermId(termId);
                sc.setCourseOfferingId(offeringId);
                sc.setUsualScore(total.add(new BigDecimal(random.nextInt(8) - 2)).setScale(1, RoundingMode.HALF_UP));
                sc.setMidScore(total.add(new BigDecimal(random.nextInt(10) - 5)).setScale(1, RoundingMode.HALF_UP));
                sc.setFinalScore(total);
                sc.setTotalScore(total);
                sc.setExamStatus("NORMAL");
                sc.setIsPass(total.compareTo(new BigDecimal("60")) >= 0 ? 1 : 0);
                sc.setGradePoint(ScoreServiceImpl.calcGradePoint(total));
                sc.setIsRetake(0);
                scores.add(sc);
            }
        }
        if (!scores.isEmpty()) {
            scoreService.saveBatch(scores, 500);
        }
        summary.put("score", scores.size());

        // 9. 考勤（当前学期，每学生 20 条）
        Term current = terms.stream().filter(t -> Integer.valueOf(1).equals(t.getIsCurrent()))
                .findFirst().orElse(terms.get(0));
        List<Attendance> attendances = new ArrayList<>();
        String[] attStatus = {"PRESENT", "PRESENT", "PRESENT", "PRESENT", "LATE", "LEAVE", "ABSENT"};
        for (Student s : allStudents) {
            for (int k = 0; k < 20; k++) {
                Attendance a = new Attendance();
                a.setStudentId(s.getId());
                a.setTermId(current.getId());
                a.setCourseOfferingId(offeringKeyToId.get(current.getId() + "#" + courses.get(k % courses.size()).getId()));
                a.setCheckDate(current.getStartDate().plusDays(random.nextInt(120)));
                a.setPeriod("第" + (1 + k % 5) + "节");
                a.setStatus(attStatus[random.nextInt(attStatus.length)]);
                attendances.add(a);
            }
        }
        if (!attendances.isEmpty()) {
            attendanceService.saveBatch(attendances, 500);
        }
        summary.put("attendance", attendances.size());

        // 10. 在线学习行为（每学生 8 条周记录）
        List<OnlineBehavior> behaviors = new ArrayList<>();
        for (Student s : allStudents) {
            for (int w = 0; w < 8; w++) {
                OnlineBehavior b = new OnlineBehavior();
                b.setStudentId(s.getId());
                b.setTermId(current.getId());
                b.setCourseId(courses.get(w % courses.size()).getId());
                b.setStatDate(current.getStartDate().plusWeeks(w));
                b.setLoginCount(1 + random.nextInt(10));
                b.setStudyMinutes(20 + random.nextInt(280));
                b.setVideoProgress(new BigDecimal(30 + random.nextInt(70)));
                b.setResourceViewCount(random.nextInt(20));
                b.setForumPostCount(random.nextInt(5));
                b.setAssignmentSubmitCount(random.nextInt(4));
                behaviors.add(b);
            }
        }
        if (!behaviors.isEmpty()) {
            onlineBehaviorService.saveBatch(behaviors, 500);
        }
        summary.put("onlineBehavior", behaviors.size());

        log.info("演示数据生成完成：{}", summary);
        return summary;
    }

    /** 清空演示数据（逻辑删除，保留表结构） */
    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        onlineBehaviorMapper.delete(new LambdaQueryWrapper<OnlineBehavior>().isNotNull(OnlineBehavior::getId));
        attendanceMapper.delete(new LambdaQueryWrapper<Attendance>().isNotNull(Attendance::getId));
        scoreMapper.delete(new LambdaQueryWrapper<Score>().isNotNull(Score::getId));
        courseOfferingMapper.delete(new LambdaQueryWrapper<CourseOffering>().isNotNull(CourseOffering::getId));
        enrollmentMapper.delete(new LambdaQueryWrapper<CourseEnrollment>().isNotNull(CourseEnrollment::getId));
        studentMapper.delete(new LambdaQueryWrapper<Student>().isNotNull(Student::getId));
        teacherMapper.delete(new LambdaQueryWrapper<Teacher>().isNotNull(Teacher::getId));
        courseMapper.delete(new LambdaQueryWrapper<Course>().isNotNull(Course::getId));
        termMapper.delete(new LambdaQueryWrapper<Term>().isNotNull(Term::getId));
        clazzMapper.delete(new LambdaQueryWrapper<Clazz>().isNotNull(Clazz::getId));
        majorMapper.delete(new LambdaQueryWrapper<Major>().isNotNull(Major::getId));
        collegeMapper.delete(new LambdaQueryWrapper<College>().isNotNull(College::getId));
    }
}
