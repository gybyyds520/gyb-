package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 偏科分析视图对象
 *
 * <p>基于学生各课程成绩与同班级（或同专业/全校）均值的差异，
 * 识别强科 / 弱科并给出偏科指数，辅助制定个性化帮扶策略。</p>
 */
@Data
public class BiasVO {

    private Long studentId;

    private String studentName;

    private String clazzName;

    /** 对比基准：CLAZZ班级 / MAJOR专业 / ALL全校 */
    private String baseline;

    /** 各课程强弱项 */
    private List<BiasItem> items;

    /** 强科数 */
    private Integer strongCount;

    /** 弱科数 */
    private Integer weakCount;

    /** 参与对比课程数 */
    private Integer total;

    /** 偏科指数 0-100：弱科数量占比 × 平均弱科差距，越高偏科越明显 */
    private BigDecimal biasIndex;

    @Data
    public static class BiasItem {
        private Long courseId;
        private String courseName;
        private String courseType;
        /** 学生课程均分 */
        private BigDecimal studentAvg;
        /** 对比基准均分 */
        private BigDecimal baselineAvg;
        /** 差值 = 学生均分 - 基准均分 */
        private BigDecimal diff;
        /** 学生在对比群体中的百分位（0-100） */
        private Integer rankPercent;
        /** STRONG强科 / WEAK弱科 / NORMAL均衡 */
        private String verdict;
        /** 建议 */
        private String advice;
    }
}
