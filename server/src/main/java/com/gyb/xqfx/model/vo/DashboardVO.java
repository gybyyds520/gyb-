package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统概览仪表盘视图对象
 */
@Data
public class DashboardVO {

    // ==================== 概览指标 ====================

    private Long studentCount;
    private Long clazzCount;
    private Long scoreCount;
    private Long predictionCount;
    private Long activeWarningCount;
    private Long reportCount;
    private Long openTicketCount;

    /** 全体平均成绩 */
    private BigDecimal avgScore;
    /** 及格率（百分比，0-100） */
    private BigDecimal passRate;

    // ==================== 图表数据 ====================

    /** 成绩分数段分布 */
    private List<NameValue> scoreDistribution;

    /** 预警等级分布 */
    private List<NameValue> warningDistribution;

    /** 班级成绩排名 */
    private List<ClazzStat> clazzRanking;

    /** 各学期成绩趋势 */
    private List<TermStat> termTrend;

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

    @Data
    public static class ClazzStat {
        private Long clazzId;
        private String clazzName;
        private Long scoreCount;
        private BigDecimal avgScore;
        private BigDecimal passRate;
        /** 优秀率（>=90 分占比，百分比） */
        private BigDecimal excellentRate;
        private Long warningCount;
    }

    @Data
    public static class TermStat {
        private Long termId;
        private String termName;
        private Long scoreCount;
        private BigDecimal avgScore;
        private BigDecimal passRate;
    }
}
