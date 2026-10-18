package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.Homework;
import com.gyb.xqfx.entity.HomeworkSubmission;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.HomeworkMapper;
import com.gyb.xqfx.mapper.HomeworkSubmissionMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业闭环（装载作业帮「AI 秒批作业 + 学练测」能力）
 *
 * <p>教师布置 → 学生作答 → 客观题自动批改 + 文本题 AI/人工批改 →
 * 错题自动收录错题本 → 完成度/正确率供行为预警参考。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final CourseOfferingMapper offeringMapper;
    private final MistakeBookService mistakeBookService;
    private final CourseSpaceService courseSpaceService;
    private final DataScopeService dataScopeService;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    /** 题目 JSON 结构：{no,type,content,answer,score,knowledgePoint} */
    public static class Question {
        public String no;
        public String type;      // CHOICE单选 / FILL填空 / ESSAY简答作文
        public String content;
        public String answer;
        public BigDecimal score;
        public String knowledgePoint;
    }

    /** 答案 JSON 结构：{no,answer} */
    public static class Answer {
        public String no;
        public String answer;
    }

    /**
     * 教师布置作业
     */
    public Long create(Long creatorId, String title, Long courseId, String courseName,
                       Long clazzId, List<Question> questions, LocalDateTime deadline) {
        return createQuiz(creatorId, title, null, courseId, courseName, clazzId, questions, deadline);
    }

    /**
     * 章节测验（学习通装载）：归属教学班 course_offering，题目结构与作业一致
     */
    public Long createQuiz(Long creatorId, String title, Long offeringId, Long courseId, String courseName,
                           Long clazzId, List<Question> questions, LocalDateTime deadline) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入测验标题");
        }
        if (clazzId == null && offeringId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择测验班级或教学班");
        }
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少添加一道题目");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Question q : questions) {
            if (q.content == null || q.content.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "题目内容不能为空");
            }
            total = total.add(q.score == null ? BigDecimal.ZERO : q.score);
        }
        try {
            Homework hw = new Homework();
            hw.setTitle(title.trim());
            hw.setCourseId(courseId);
            hw.setCourseOfferingId(offeringId);
            hw.setCourseName(courseName);
            hw.setClazzId(clazzId);
            hw.setCreatorId(creatorId);
            hw.setQuestionJson(objectMapper.writeValueAsString(questions));
            hw.setTotalScore(total.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.valueOf(100) : total);
            hw.setDeadline(deadline);
            hw.setStatus("ACTIVE");
            hw.setCreateTime(LocalDateTime.now());
            hw.setUpdateTime(LocalDateTime.now());
            hw.setIsDeleted(0);
            homeworkMapper.insert(hw);
            log.info("章节测验布置: id={}, title={}, offering={}, clazz={}", hw.getId(), hw.getTitle(), offeringId, clazzId);
            return hw.getId();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "测验保存失败：" + e.getMessage());
        }
    }

    /**
     * 教师端：按班级查看作业列表（含完成度/平均分）
     */
    public List<Map<String, Object>> teacherHomeworks(Long userId, Long clazzId) {
        List<Long> scopeClazz = clazzId == null ? dataScopeService.visibleClazzIds(userId) : List.of(clazzId);
        if (scopeClazz.isEmpty()) {
            return List.of();
        }
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .in(Homework::getClazzId, scopeClazz)
                .orderByDesc(Homework::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Homework hw : list) {
            result.add(withStats(hw));
        }
        return result;
    }

    /**
     * 教师端：教学班的章节测验列表（学习通装载）
     */
    public List<Map<String, Object>> offeringQuizzes(Long offeringId, Long userId) {
        CourseOffering offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学班不存在");
        }
        if (!dataScopeService.hasAllDataScope(userId)
                && !courseSpaceService.teacherOwnsOffering(userId, offeringId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该教学班");
        }
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getCourseOfferingId, offeringId)
                .orderByDesc(Homework::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Homework hw : list) {
            result.add(withStats(hw));
        }
        return result;
    }

    /**
     * 学生端：本人课程章节测验列表（学习通装载）
     */
    public List<Map<String, Object>> studentQuizzes(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            return List.of();
        }
        List<Long> enrolledIds = courseSpaceService.enrolledOfferingIds(studentId);
        List<Long> clazzIds = student.getClazzId() == null ? List.of()
                : offeringMapper.selectList(new LambdaQueryWrapper<CourseOffering>()
                        .eq(CourseOffering::getClazzId, student.getClazzId()))
                .stream().map(CourseOffering::getId).toList();
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        ids.addAll(enrolledIds);
        ids.addAll(clazzIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .in(Homework::getCourseOfferingId, ids)
                .orderByDesc(Homework::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Homework hw : list) {
            Map<String, Object> row = withStats(hw);
            row.put("offeringId", hw.getCourseOfferingId());
            row.put("submitted", submissionMapper.selectCount(new LambdaQueryWrapper<HomeworkSubmission>()
                    .eq(HomeworkSubmission::getHomeworkId, hw.getId())
                    .eq(HomeworkSubmission::getStudentId, studentId)) > 0);
            result.add(row);
        }
        return result;
    }

    /**
     * 作业详情 + 完成度统计（教师）
     */
    public Map<String, Object> detailForTeacher(Long homeworkId, Long userId) {
        Homework hw = homeworkMapper.selectById(homeworkId);
        if (hw == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        if (!dataScopeService.visibleClazzIds(userId).contains(hw.getClazzId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该班级作业");
        }
        Map<String, Object> data = withStats(hw);
        data.put("questions", parseQuestions(hw.getQuestionJson()));
        // 提交明细（学生姓名 + 状态 + 得分）
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClazzId, hw.getClazzId()));
        Map<Long, HomeworkSubmission> subMap = new LinkedHashMap<>();
        for (HomeworkSubmission s : submissionMapper.selectList(new LambdaQueryWrapper<HomeworkSubmission>()
                .eq(HomeworkSubmission::getHomeworkId, homeworkId))) {
            subMap.put(s.getStudentId(), s);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Student st : students) {
            HomeworkSubmission s = subMap.get(st.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", st.getId());
            row.put("studentName", st.getName());
            row.put("studentNo", st.getStudentNo());
            row.put("submissionId", s == null ? null : s.getId());
            row.put("status", s == null ? "PENDING" : s.getStatus());
            row.put("score", s == null ? null : s.getScore());
            row.put("autoScore", s == null ? null : s.getAutoScore());
            row.put("feedback", s == null ? null : s.getFeedback());
            row.put("submitTime", s == null ? null : s.getSubmitTime());
            rows.add(row);
        }
        data.put("submissions", rows);
        return data;
    }

    /**
     * 学生端：我的作业（待办/已交/得分/是否逾期）
     */
    public List<Map<String, Object>> myHomework(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getClazzId, student.getClazzId())
                .eq(Homework::getStatus, "ACTIVE")
                .orderByDesc(Homework::getCreateTime));
        Map<Long, HomeworkSubmission> subMap = new LinkedHashMap<>();
        for (HomeworkSubmission s : submissionMapper.selectList(new LambdaQueryWrapper<HomeworkSubmission>()
                .eq(HomeworkSubmission::getStudentId, studentId))) {
            subMap.put(s.getHomeworkId(), s);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Homework hw : list) {
            HomeworkSubmission s = subMap.get(hw.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", hw.getId());
            row.put("title", hw.getTitle());
            row.put("courseName", hw.getCourseName());
            row.put("totalScore", hw.getTotalScore());
            row.put("deadline", hw.getDeadline());
            row.put("questionCount", countQuestions(hw.getQuestionJson()));
            row.put("submitted", s != null);
            row.put("status", s == null ? "PENDING" : s.getStatus());
            row.put("score", s == null ? null : s.getScore());
            row.put("feedback", s == null ? null : s.getFeedback());
            if (s == null && hw.getDeadline() != null && hw.getDeadline().isBefore(LocalDateTime.now())) {
                row.put("overdue", true);
            } else {
                row.put("overdue", false);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 学生查看作业详情（含题目）
     */
    public Map<String, Object> studentDetail(Long homeworkId, Long studentId) {
        Homework hw = homeworkMapper.selectById(homeworkId);
        if (hw == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null || !student.getClazzId().equals(hw.getClazzId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该作业不属于你的班级");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", hw.getId());
        data.put("title", hw.getTitle());
        data.put("courseName", hw.getCourseName());
        data.put("totalScore", hw.getTotalScore());
        data.put("deadline", hw.getDeadline());
        data.put("questions", parseQuestions(hw.getQuestionJson()));
        HomeworkSubmission s = submissionMapper.selectOne(new LambdaQueryWrapper<HomeworkSubmission>()
                .eq(HomeworkSubmission::getHomeworkId, homeworkId)
                .eq(HomeworkSubmission::getStudentId, studentId));
        data.put("submission", s);
        return data;
    }

    /**
     * 学生提交作业：客观题（CHOICE/FILL）自动批改，错题自动收录错题本
     */
    public void submit(Long studentId, Long homeworkId, List<Answer> answers) {
        Homework hw = homeworkMapper.selectById(homeworkId);
        if (hw == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null || !student.getClazzId().equals(hw.getClazzId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该作业不属于你的班级");
        }
        if ("CLOSED".equals(hw.getStatus())
                || (hw.getDeadline() != null && hw.getDeadline().isBefore(LocalDateTime.now()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "作业已截止，无法提交");
        }
        List<Question> questions = parseQuestions(hw.getQuestionJson());
        Map<String, String> answerMap = new LinkedHashMap<>();
        if (answers != null) {
            for (Answer a : answers) {
                answerMap.put(a.no, a.answer == null ? "" : a.answer.trim());
            }
        }
        // 客观题自动判分（CHOICE/FILL/JUDGE）
        BigDecimal autoScore = BigDecimal.ZERO;
        List<Question> wrongWithAnswer = new ArrayList<>();
        for (Question q : questions) {
            String stuAnswer = answerMap.getOrDefault(q.no, "");
            if ("CHOICE".equals(q.type) || "FILL".equals(q.type) || "JUDGE".equals(q.type)) {
                boolean right = judgeRight(q.type, q.answer, stuAnswer);
                if (right) {
                    autoScore = autoScore.add(q.score == null ? BigDecimal.ZERO : q.score);
                } else {
                    wrongWithAnswer.add(q);
                }
            }
            // ESSAY 不自动判分，由教师/AI 批改
        }

        try {
            String answersJson = objectMapper.writeValueAsString(answers == null ? List.of() : answers);
            HomeworkSubmission sub = submissionMapper.selectOne(new LambdaQueryWrapper<HomeworkSubmission>()
                    .eq(HomeworkSubmission::getHomeworkId, homeworkId)
                    .eq(HomeworkSubmission::getStudentId, studentId));
            LocalDateTime now = LocalDateTime.now();
            if (sub == null) {
                sub = new HomeworkSubmission();
                sub.setHomeworkId(homeworkId);
                sub.setStudentId(studentId);
                sub.setCreateTime(now);
                sub.setUpdateTime(now);
                sub.setIsDeleted(0);
                sub.setSubmitTime(now);
                sub.setAnswersJson(answersJson);
                sub.setAutoScore(autoScore);
                sub.setScore(autoScore);
                sub.setStatus("AUTO_GRADED");
                submissionMapper.insert(sub);
            } else {
                sub.setUpdateTime(now);
                sub.setSubmitTime(now);
                sub.setAnswersJson(answersJson);
                sub.setAutoScore(autoScore);
                sub.setScore(autoScore);
                sub.setStatus("AUTO_GRADED");
                submissionMapper.updateById(sub);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "提交保存失败：" + e.getMessage());
        }
        // 错题自动收录
        for (Question q : wrongWithAnswer) {
            try {
                mistakeBookService.add(studentId, hw.getCourseId(), hw.getCourseName(),
                        q.knowledgePoint, q.type, q.content,
                        answerMap.getOrDefault(q.no, ""), q.answer,
                        "HOMEWORK", homeworkId, "未掌握");
            } catch (Exception e) {
                log.warn("错题自动收录失败: {}", e.getMessage());
            }
        }
        log.info("作业提交并自动批改: student={}, homework={}, autoScore={}", studentId, homeworkId, autoScore);
    }

    /** 客观题判分：判断题归一化（对/√/T/正确 与 错/×/F/错误），其余忽略大小写比较 */
    private boolean judgeRight(String type, String answer, String stuAnswer) {
        if (answer == null || stuAnswer == null) {
            return false;
        }
        if ("JUDGE".equals(type)) {
            return normalizeJudge(answer) == normalizeJudge(stuAnswer);
        }
        return answer.trim().equalsIgnoreCase(stuAnswer.trim());
    }

    private boolean normalizeJudge(String v) {
        String s = v.trim();
        return s.equals("对") || s.equals("√") || s.equals("T") || s.equals("t")
                || s.equals("正确") || s.equals("YES") || s.equals("yes") || s.equals("true") || s.equals("TRUE");
    }

    /**
     * 教师人工补批文本题
     */
    public void grade(Long teacherId, Long homeworkId, Long submissionId,
                      BigDecimal manualScore, String feedback) {
        Homework hw = homeworkMapper.selectById(homeworkId);
        if (hw == null || !dataScopeService.visibleClazzIds(teacherId).contains(hw.getClazzId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权批改该作业");
        }
        HomeworkSubmission sub = submissionMapper.selectById(submissionId);
        if (sub == null || !sub.getHomeworkId().equals(homeworkId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        sub.setScore(manualScore);
        sub.setFeedback(feedback);
        sub.setStatus("GRADED");
        sub.setGradeTime(LocalDateTime.now());
        sub.setUpdateTime(LocalDateTime.now());
        submissionMapper.updateById(sub);
        log.info("作业人工批改: submission={}, score={}", submissionId, manualScore);
    }

    /**
     * AI 批改文本题（本地大模型评分 + 评语）
     */
    public Map<String, Object> aiGrade(Long teacherId, Long homeworkId, Long submissionId) {
        Homework hw = homeworkMapper.selectById(homeworkId);
        if (hw == null || !dataScopeService.visibleClazzIds(teacherId).contains(hw.getClazzId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权批改该作业");
        }
        HomeworkSubmission sub = submissionMapper.selectById(submissionId);
        if (sub == null || !sub.getHomeworkId().equals(homeworkId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        if (!ollamaClient.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "本地大模型不可用，请先启动 Ollama");
        }
        List<Question> questions = parseQuestions(hw.getQuestionJson());
        List<Answer> answers = parseAnswers(sub.getAnswersJson());
        Map<String, String> answerMap = new LinkedHashMap<>();
        for (Answer a : answers) {
            answerMap.put(a.no, a.answer);
        }
        // 只批文本题
        StringBuilder sb = new StringBuilder();
        for (Question q : questions) {
            if ("ESSAY".equals(q.type)) {
                sb.append("题目").append(q.no).append("：").append(q.content)
                        .append("\n学生作答：").append(answerMap.getOrDefault(q.no, "（未作答）")).append("\n");
            }
        }
        if (sb.length() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "本作业没有需要 AI 批改的文本题");
        }
        String systemPrompt = "你是严谨的作业批改老师。根据题目和学生作答，输出 JSON：{\"score\": 0-100 数字, \"feedback\": \"评语（指出问题与改进建议，150字内）\"}。只输出 JSON。";
        OllamaClient.ChatResult res = ollamaClient.chat(systemPrompt, sb.toString());
        BigDecimal aiScore = null;
        String feedback = res.content;
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    res.content.replaceAll("```json|```", "").trim(), new TypeReference<>() {});
            Object s = parsed.get("score");
            if (s instanceof Number n) {
                aiScore = BigDecimal.valueOf(n.doubleValue());
            }
            Object f = parsed.get("feedback");
            if (f != null) {
                feedback = f.toString();
            }
        } catch (Exception ignored) {
            // 解析失败则整段文本作为评语，分数留空由教师定
        }
        BigDecimal finalScore = aiScore == null ? null : aiScore;
        if (aiScore != null) {
            sub.setScore(aiScore);
        }
        sub.setFeedback(feedback);
        sub.setStatus("GRADED");
        sub.setGradeTime(LocalDateTime.now());
        sub.setUpdateTime(LocalDateTime.now());
        submissionMapper.updateById(sub);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", finalScore);
        result.put("feedback", feedback);
        result.put("model", res.model);
        log.info("作业 AI 批改: submission={}, score={}", submissionId, finalScore);
        return result;
    }

    /**
     * 家长端：绑定子女的作业动态（最新作业 + 完成/得分）
     */
    public List<Map<String, Object>> parentChildrenHomework(Long parentUserId) {
        List<Long> childIds = dataScopeService.childStudentIds(parentUserId);
        if (childIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> stuMap = new LinkedHashMap<>();
        for (Student s : studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getId, childIds))) {
            stuMap.put(s.getId(), s);
        }
        List<Homework> list = homeworkMapper.selectList(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStatus, "ACTIVE")
                .orderByDesc(Homework::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Homework hw : list) {
            for (Long childId : childIds) {
                Student st = stuMap.get(childId);
                if (st == null || !st.getClazzId().equals(hw.getClazzId())) {
                    continue;
                }
                HomeworkSubmission sub = submissionMapper.selectOne(new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getHomeworkId, hw.getId())
                        .eq(HomeworkSubmission::getStudentId, childId));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("homeworkId", hw.getId());
                row.put("title", hw.getTitle());
                row.put("courseName", hw.getCourseName());
                row.put("deadline", hw.getDeadline());
                row.put("studentName", st.getName());
                row.put("submitted", sub != null);
                row.put("score", sub == null ? null : sub.getScore());
                row.put("totalScore", hw.getTotalScore());
                row.put("feedback", sub == null ? null : sub.getFeedback());
                result.add(row);
            }
        }
        return result;
    }

    /** 作业 + 完成度统计 */
    private Map<String, Object> withStats(Homework hw) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", hw.getId());
        data.put("title", hw.getTitle());
        data.put("courseName", hw.getCourseName());
        data.put("clazzId", hw.getClazzId());
        Clazz clazz = clazzMapper.selectById(hw.getClazzId());
        data.put("clazzName", clazz == null ? null : clazz.getClazzName());
        data.put("totalScore", hw.getTotalScore());
        data.put("deadline", hw.getDeadline());
        data.put("status", hw.getStatus());
        data.put("questionCount", countQuestions(hw.getQuestionJson()));

        long studentTotal = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getClazzId, hw.getClazzId()));
        List<HomeworkSubmission> subs = submissionMapper.selectList(new LambdaQueryWrapper<HomeworkSubmission>()
                .eq(HomeworkSubmission::getHomeworkId, hw.getId()));
        long submitted = subs.size();
        BigDecimal sum = BigDecimal.ZERO;
        long graded = 0;
        for (HomeworkSubmission s : subs) {
            if (s.getScore() != null) {
                sum = sum.add(s.getScore());
                graded++;
            }
        }
        data.put("studentTotal", studentTotal);
        data.put("submitted", submitted);
        data.put("submittedCount", submitted);
        data.put("studentCount", studentTotal);
        data.put("submitRate", studentTotal == 0 ? 0 : Math.round(submitted * 1000.0 / studentTotal) / 10.0);
        data.put("avgScore", graded == 0 ? null : sum.divide(BigDecimal.valueOf(graded), 1, java.math.RoundingMode.HALF_UP));
        return data;
    }

    private List<Question> parseQuestions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Question>>() {});
        } catch (Exception e) {
            log.warn("题目 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Answer> parseAnswers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Answer>>() {});
        } catch (Exception e) {
            log.warn("答案 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private int countQuestions(String json) {
        return parseQuestions(json).size();
    }
}
