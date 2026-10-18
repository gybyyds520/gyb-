package com.gyb.xqfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.model.vo.DashboardVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 按学期统计成绩总览（平均成绩、及格率、优秀率）
     */
    @Select("SELECT s.term_id AS termId, t.term_name AS termName, COUNT(*) AS scoreCount, " +
            "AVG(s.total_score) AS avgScore, " +
            "SUM(CASE WHEN s.total_score >= 60 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS passRate " +
            "FROM score s LEFT JOIN term t ON t.id = s.term_id " +
            "WHERE s.is_deleted = 0 AND s.total_score IS NOT NULL " +
            "GROUP BY s.term_id, t.term_name " +
            "ORDER BY t.id")
    List<DashboardVO.TermStat> statByTerm();

    /**
     * 按班级统计成绩（平均成绩、及格率、优秀率），用于班级对比排名
     */
    @Select("SELECT c.id AS clazzId, c.clazz_name AS clazzName, COUNT(*) AS scoreCount, " +
            "AVG(s.total_score) AS avgScore, " +
            "SUM(CASE WHEN s.total_score >= 60 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS passRate, " +
            "SUM(CASE WHEN s.total_score >= 90 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS excellentRate " +
            "FROM score s " +
            "JOIN student st ON st.id = s.student_id " +
            "JOIN clazz c ON c.id = st.clazz_id " +
            "WHERE s.is_deleted = 0 AND s.total_score IS NOT NULL " +
            "AND (#{termId} IS NULL OR s.term_id = #{termId}) " +
            "GROUP BY c.id, c.clazz_name " +
            "ORDER BY avgScore DESC")
    List<DashboardVO.ClazzStat> statByClazz(@Param("termId") Long termId);

    /**
     * 统计成绩分数段分布：[0,60) [60,70) [70,80) [80,90) [90,100]
     */
    @Select("SELECT " +
            "SUM(CASE WHEN total_score < 60 THEN 1 ELSE 0 END) AS seg1, " +
            "SUM(CASE WHEN total_score >= 60 AND total_score < 70 THEN 1 ELSE 0 END) AS seg2, " +
            "SUM(CASE WHEN total_score >= 70 AND total_score < 80 THEN 1 ELSE 0 END) AS seg3, " +
            "SUM(CASE WHEN total_score >= 80 AND total_score < 90 THEN 1 ELSE 0 END) AS seg4, " +
            "SUM(CASE WHEN total_score >= 90 THEN 1 ELSE 0 END) AS seg5 " +
            "FROM score WHERE is_deleted = 0 AND total_score IS NOT NULL " +
            "AND (#{termId} IS NULL OR term_id = #{termId})")
    java.util.Map<String, Object> statSegments(@Param("termId") Long termId);
}
