package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.ClassPerformance;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClassPerformanceMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.vo.PerformanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课堂表现服务：记录学生参与度、作业得分与互动情况，并做学期汇总
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final ClassPerformanceMapper performanceMapper;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;

    /**
     * 记录课堂表现
     */
    public ClassPerformance record(ClassPerformance record) {
        if (record.getRecordDate() == null) {
            record.setRecordDate(LocalDate.now());
        }
        if (record.getRemark() == null) {
            record.setRemark("");
        }
        if (record.getQuestionCount() == null) {
            record.setQuestionCount(0);
        }
        performanceMapper.insert(record);
        return record;
    }

    /**
     * 查询学生课堂表现记录
     */
    public List<ClassPerformance> listByStudent(Long studentId, Long termId) {
        return performanceMapper.selectList(new LambdaQueryWrapper<ClassPerformance>()
                .eq(ClassPerformance::getStudentId, studentId)
                .eq(termId != null, ClassPerformance::getTermId, termId)
                .orderByDesc(ClassPerformance::getRecordDate));
    }

    /**
     * 学生课堂表现汇总：平均参与度、平均作业分、互动总次数
     */
    public PerformanceVO summary(Long studentId, Long termId) {
        List<ClassPerformance> records = listByStudent(studentId, termId);
        PerformanceVO vo = new PerformanceVO();
        Student student = studentMapper.selectById(studentId);
        vo.setStudentId(studentId);
        if (student != null) {
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getName());
        }
        vo.setRecordCount(records.size());
        vo.setAvgParticipation(avg(records.stream()
                .map(ClassPerformance::getParticipationScore).filter(java.util.Objects::nonNull).toList()));
        vo.setAvgHomework(avg(records.stream()
                .map(ClassPerformance::getHomeworkScore).filter(java.util.Objects::nonNull).toList()));
        vo.setTotalQuestions(records.stream()
                .mapToInt(r -> r.getQuestionCount() == null ? 0 : r.getQuestionCount()).sum());
        vo.setLevel(evalLevel(vo.getAvgParticipation()));
        return vo;
    }

    /**
     * 班级课堂表现排名：按平均参与度降序
     */
    public List<PerformanceVO> ranking(Long termId, int limit) {
        List<ClassPerformance> all = performanceMapper.selectList(new LambdaQueryWrapper<ClassPerformance>()
                .eq(termId != null, ClassPerformance::getTermId, termId));
        Map<Long, List<ClassPerformance>> byStudent = all.stream()
                .collect(Collectors.groupingBy(ClassPerformance::getStudentId));
        List<PerformanceVO> list = new java.util.ArrayList<>();
        byStudent.forEach((studentId, records) -> {
            PerformanceVO vo = new PerformanceVO();
            Student student = studentMapper.selectById(studentId);
            vo.setStudentId(studentId);
            if (student != null) {
                vo.setStudentNo(student.getStudentNo());
                vo.setStudentName(student.getName());
            }
            vo.setRecordCount(records.size());
            vo.setAvgParticipation(avg(records.stream()
                    .map(ClassPerformance::getParticipationScore).filter(java.util.Objects::nonNull).toList()));
            vo.setAvgHomework(avg(records.stream()
                    .map(ClassPerformance::getHomeworkScore).filter(java.util.Objects::nonNull).toList()));
            vo.setTotalQuestions(records.stream()
                    .mapToInt(r -> r.getQuestionCount() == null ? 0 : r.getQuestionCount()).sum());
            vo.setLevel(evalLevel(vo.getAvgParticipation()));
            list.add(vo);
        });
        list.sort((a, b) -> b.getAvgParticipation().compareTo(a.getAvgParticipation()));
        return limit > 0 ? list.stream().limit(limit).toList() : list;
    }

    /**
     * 生成演示课堂表现数据：依据学生成绩水平生成自洽的参与度与作业分
     *
     * @return 生成记录数
     */
    public int generateDemo(Long termId, int timesPerStudent) {
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore));
        if (scores.isEmpty()) {
            throw new IllegalArgumentException("该学期暂无成绩数据，无法生成课堂表现");
        }
        java.util.Random random = new java.util.Random(termId == null ? 7 : termId);
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Score sc : scores) {
            double ability = sc.getTotalScore().doubleValue() / 100.0;
            for (int i = 0; i < timesPerStudent; i++) {
                ClassPerformance record = new ClassPerformance();
                record.setStudentId(sc.getStudentId());
                record.setCourseOfferingId(sc.getCourseOfferingId());
                record.setTermId(sc.getTermId());
                record.setRecordDate(today.minusDays((long) (i * 7 + random.nextInt(5))));
                double participation = Math.max(0, Math.min(10, ability * 8 + random.nextGaussian() * 1.5));
                record.setParticipationScore(BigDecimal.valueOf(participation).setScale(2, RoundingMode.HALF_UP));
                double homework = Math.max(0, Math.min(100, ability * 85 + random.nextGaussian() * 8));
                record.setHomeworkScore(BigDecimal.valueOf(homework).setScale(2, RoundingMode.HALF_UP));
                record.setQuestionCount(Math.max(0, (int) Math.round(ability * 5 + random.nextGaussian())));
                record.setRemark("演示数据");
                record.setCreateTime(java.time.LocalDateTime.now());
                record.setUpdateTime(java.time.LocalDateTime.now());
                record.setIsDeleted(0);
                performanceMapper.insert(record);
                count++;
            }
        }
        log.info("生成课堂表现演示数据 {} 条", count);
        return count;
    }

    /** 参与度评级：>=8 积极，>=5 一般，否则待提升 */
    public String evalLevel(BigDecimal participation) {
        if (participation == null) {
            return "待提升";
        }
        if (participation.compareTo(new BigDecimal("8")) >= 0) {
            return "积极";
        }
        if (participation.compareTo(new BigDecimal("5")) >= 0) {
            return "一般";
        }
        return "待提升";
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
