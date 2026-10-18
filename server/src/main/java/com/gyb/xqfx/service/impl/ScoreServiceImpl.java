package com.gyb.xqfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.model.query.ScoreQuery;
import com.gyb.xqfx.model.vo.ScoreVO;
import com.gyb.xqfx.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩服务实现
 */
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl extends ServiceImpl<ScoreMapper, Score> implements ScoreService {

    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final TermMapper termMapper;
    private final ClazzMapper clazzMapper;

    @Override
    public IPage<ScoreVO> pageScores(long current, long size, ScoreQuery query) {
        LambdaQueryWrapper<Score> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.eq(query.getStudentId() != null, Score::getStudentId, query.getStudentId());
            wrapper.eq(query.getCourseId() != null, Score::getCourseId, query.getCourseId());
            wrapper.eq(query.getTermId() != null, Score::getTermId, query.getTermId());
            wrapper.eq(Boolean.TRUE.equals(query.getOnlyFailed()), Score::getIsPass, 0);
        }
        wrapper.orderByDesc(Score::getTermId).orderByAsc(Score::getStudentId);

        Page<Score> page = this.page(new Page<>(current, size), wrapper);
        List<Score> records = page.getRecords();

        Map<Long, Student> students = loadEntityMap(records, Score::getStudentId, studentMapper, Student::getId);
        Map<Long, Course> courses = loadEntityMap(records, Score::getCourseId, courseMapper, Course::getId);
        Map<Long, Term> terms = loadEntityMap(records, Score::getTermId, termMapper, Term::getId);

        List<Long> clazzIds = students.values().stream().map(Student::getClazzId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> clazzNames = clazzIds.isEmpty() ? Map.of()
                : clazzMapper.selectBatchIds(clazzIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getClazzName, (a, b) -> a));

        List<ScoreVO> vos = records.stream().map(s -> {
            ScoreVO vo = new ScoreVO();
            BeanUtils.copyProperties(s, vo);
            Student stu = students.get(s.getStudentId());
            if (stu != null) {
                vo.setStudentNo(stu.getStudentNo());
                vo.setStudentName(stu.getName());
                vo.setClazzName(clazzNames.get(stu.getClazzId()));
            }
            Course c = courses.get(s.getCourseId());
            if (c != null) {
                vo.setCourseName(c.getCourseName());
                vo.setCourseCode(c.getCourseCode());
            }
            Term t = terms.get(s.getTermId());
            if (t != null) {
                vo.setTermName(t.getTermName());
            }
            return vo;
        }).collect(Collectors.toList());

        Page<ScoreVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 按外键批量加载关联实体，避免 N+1 查询
     */
    private <E> Map<Long, E> loadEntityMap(List<Score> scores,
                                           Function<Score, Long> idGetter,
                                           com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper,
                                           Function<E, Long> entityIdGetter) {
        List<Long> ids = scores.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(entityIdGetter, Function.identity(), (a, b) -> a));
    }

    private static final BigDecimal PASS_LINE = new BigDecimal("60");
    private static final BigDecimal BASE = new BigDecimal("50");
    private static final BigDecimal DIVISOR = new BigDecimal("10");
    private static final BigDecimal MAX_POINT = new BigDecimal("5.00");

    /**
     * 计算绩点：不及格(<60)为 0，其余 (score - 50) / 10，上限 5.0
     */
    public static BigDecimal calcGradePoint(BigDecimal score) {
        if (score == null || score.compareTo(PASS_LINE) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal gp = score.subtract(BASE).divide(DIVISOR, 2, RoundingMode.HALF_UP);
        return gp.min(MAX_POINT);
    }
}
