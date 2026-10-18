package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学生画像视图对象
 */
@Data
public class ProfileVO {

    private Long studentId;
    private String studentNo;
    private String studentName;
    private String clazzName;

    /** 学业水平 0-100 */
    private BigDecimal academicScore;
    /** 学习投入 0-100 */
    private BigDecimal engagementScore;
    /** 行为表现 0-100 */
    private BigDecimal behaviorScore;
    /** 发展潜力 0-100 */
    private BigDecimal potentialScore;
    /** 风险指数 0-100 */
    private BigDecimal riskScore;

    private LocalDate snapshotDate;
    private String dimensionJson;

    /** 历史趋势：维度 -> 分数序列 */
    private Map<String, List<Object>> trend;

    private List<TagItem> tags;

    @Data
    public static class TagItem {
        private String tagCode;
        private String tagName;
        private String category;
        private String color;
        private String source;
    }
}
