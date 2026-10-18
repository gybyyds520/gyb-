package com.gyb.xqfx.model.vo;

import com.gyb.xqfx.entity.ExamPaper;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试卷分析结果视图对象
 */
@Data
public class ExamAnalysisVO {

    private ExamPaper paper;

    /** 题目明细统计 */
    private List<QuestionStat> questions;

    /** 知识点掌握度 */
    private List<KnowledgeMastery> knowledge;

    /** 学生总分分布 */
    private List<NameValue> distribution;

    /** 参试人数 */
    private Integer studentCount;

    @Data
    public static class QuestionStat {
        private Long questionId;
        private String questionNo;
        private String questionType;
        private String knowledgePoint;
        private BigDecimal fullScore;
        private BigDecimal avgScore;
        private BigDecimal scoreRate;
        private BigDecimal difficulty;
        private BigDecimal discrimination;
        private BigDecimal stdDev;
    }

    @Data
    public static class KnowledgeMastery {
        private String knowledgePoint;
        private BigDecimal fullScore;
        private BigDecimal avgScore;
        private BigDecimal scoreRate;
        /** 掌握度评价：掌握较好 / 一般 / 薄弱 */
        private String level;
    }

    @Data
    public static class NameValue {
        private String name;
        private Number value;

        public NameValue() {
        }

        public NameValue(String name, Number value) {
            this.name = name;
            this.value = value;
        }
    }
}
