package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.MistakeBook;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.MistakeBookMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本：错题收录（考试/作业自动 + 手动）、按知识点/错因统计、掌握标记
 *
 * <p>装载作业帮「错题本」能力：错题自动整理、按考点分类、举一反三巩固。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MistakeBookService {

    private final MistakeBookMapper mistakeBookMapper;
    private final StudentMapper studentMapper;
    private final DataScopeService dataScopeService;

    /**
     * 收录错题（内部通用；作业/考试联动调用）
     */
    public Long add(Long studentId, Long courseId, String courseName, String knowledgePoint,
                    String questionType, String questionContent, String wrongAnswer,
                    String rightAnswer, String sourceType, Long sourceId, String mistakeReason) {
        if (studentId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少学生");
        }
        // 去重：同一学生 + 同一来源 + 同一题目内容 已存在则跳过（作业/考试批量收录场景）
        Long exists = mistakeBookMapper.selectCount(new LambdaQueryWrapper<MistakeBook>()
                .eq(MistakeBook::getStudentId, studentId)
                .eq(MistakeBook::getSourceType, sourceType == null ? "MANUAL" : sourceType)
                .eq(sourceId != null, MistakeBook::getSourceId, sourceId)
                .eq(MistakeBook::getQuestionContent, questionContent == null ? "" : questionContent)
                .eq(MistakeBook::getStatus, "UNRESOLVED"));
        if (exists != null && exists > 0) {
            return null;
        }
        MistakeBook book = new MistakeBook();
        book.setStudentId(studentId);
        book.setCourseId(courseId);
        book.setCourseName(courseName);
        book.setKnowledgePoint(knowledgePoint);
        book.setQuestionType(questionType == null || questionType.isBlank() ? "OTHER" : questionType);
        book.setQuestionContent(questionContent);
        book.setWrongAnswer(wrongAnswer);
        book.setRightAnswer(rightAnswer);
        book.setSourceType(sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType);
        book.setSourceId(sourceId);
        book.setMistakeReason(mistakeReason == null || mistakeReason.isBlank() ? "未掌握" : mistakeReason);
        book.setStatus("UNRESOLVED");
        book.setCreateTime(LocalDateTime.now());
        book.setUpdateTime(LocalDateTime.now());
        book.setIsDeleted(0);
        mistakeBookMapper.insert(book);
        log.info("错题收录: student={}, course={}, source={}", studentId, courseName, book.getSourceType());
        return book.getId();
    }

    /**
     * 学生本人错题本（按课程分组）
     */
    public List<Map<String, Object>> myList(Long studentId) {
        List<MistakeBook> list = mistakeBookMapper.selectList(new LambdaQueryWrapper<MistakeBook>()
                .eq(MistakeBook::getStudentId, studentId)
                .orderByDesc(MistakeBook::getCreateTime));
        Map<String, List<MistakeBook>> byCourse = new LinkedHashMap<>();
        for (MistakeBook m : list) {
            String key = m.getCourseName() == null || m.getCourseName().isBlank() ? "未分类" : m.getCourseName();
            byCourse.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<MistakeBook>> e : byCourse.entrySet()) {
            long unresolved = e.getValue().stream().filter(m -> "UNRESOLVED".equals(m.getStatus())).count();
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("courseName", e.getKey());
            group.put("total", e.getValue().size());
            group.put("unresolved", unresolved);
            group.put("items", e.getValue());
            result.add(group);
        }
        return result;
    }

    /**
     * 错题统计：总量、未掌握、知识点 TOP、错因分布
     */
    public Map<String, Object> stats(Long studentId) {
        List<MistakeBook> list = mistakeBookMapper.selectList(new LambdaQueryWrapper<MistakeBook>()
                .eq(MistakeBook::getStudentId, studentId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", list.size());
        data.put("unresolved", list.stream().filter(m -> "UNRESOLVED".equals(m.getStatus())).count());
        data.put("mastered", list.stream().filter(m -> "MASTERED".equals(m.getStatus())).count());

        Map<String, Long> byPoint = new LinkedHashMap<>();
        for (MistakeBook m : list) {
            String key = m.getKnowledgePoint() == null || m.getKnowledgePoint().isBlank()
                    ? "未分类" : m.getKnowledgePoint();
            byPoint.merge(key, 1L, Long::sum);
        }
        data.put("knowledgePoints", byPoint);

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (MistakeBook m : list) {
            String key = m.getMistakeReason() == null || m.getMistakeReason().isBlank()
                    ? "未掌握" : m.getMistakeReason();
            byReason.merge(key, 1L, Long::sum);
        }
        data.put("mistakeReasons", byReason);
        return data;
    }

    /**
     * 标记已掌握（仅本人或教师）
     */
    public void markMastered(Long id) {
        MistakeBook m = mistakeBookMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "错题记录不存在");
        }
        dataScopeService.requireStudentAccess(m.getStudentId());
        m.setStatus("MASTERED");
        m.setUpdateTime(LocalDateTime.now());
        mistakeBookMapper.updateById(m);
    }

    /**
     * 删除错题（仅本人或教师）
     */
    public void remove(Long id) {
        MistakeBook m = mistakeBookMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "错题记录不存在");
        }
        dataScopeService.requireStudentAccess(m.getStudentId());
        m.setIsDeleted(1);
        m.setUpdateTime(LocalDateTime.now());
        mistakeBookMapper.updateById(m);
    }

    /**
     * 教师按数据范围查看某学生错题（scope 为当前用户可见学生，未传入则自动计算）
     */
    public List<Map<String, Object>> listOfStudent(Long studentId) {
        dataScopeService.requireStudentAccess(studentId);
        Student s = studentMapper.selectById(studentId);
        List<MistakeBook> list = mistakeBookMapper.selectList(new LambdaQueryWrapper<MistakeBook>()
                .eq(MistakeBook::getStudentId, studentId)
                .orderByDesc(MistakeBook::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (MistakeBook m : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("studentName", s == null ? null : s.getName());
            row.put("courseName", m.getCourseName());
            row.put("knowledgePoint", m.getKnowledgePoint());
            row.put("questionType", m.getQuestionType());
            row.put("questionContent", m.getQuestionContent());
            row.put("wrongAnswer", m.getWrongAnswer());
            row.put("rightAnswer", m.getRightAnswer());
            row.put("sourceType", m.getSourceType());
            row.put("mistakeReason", m.getMistakeReason());
            row.put("status", m.getStatus());
            row.put("createTime", m.getCreateTime());
            result.add(row);
        }
        return result;
    }
}
