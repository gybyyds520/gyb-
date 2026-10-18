package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学习资源推荐项
 *
 * <p>由目标差距与偏科分析规则化生成：针对薄弱课程给出资源类型、
 * 理由与具体行动建议，不依赖外部资源库，可扩展为真实资源链接。</p>
 */
@Data
public class ResourceRecommendVO {

    /** 资源类型：COURSE课程/VIDEO视频/PRACTICE练习/BOOK教材/COACH辅导 */
    private String resourceType;

    private String resourceTypeText;

    /** 推荐标题 */
    private String title;

    /** 推荐理由（数据驱动） */
    private String reason;

    /** 建议行动 */
    private String action;

    /** 关联课程（弱科目标课程） */
    private Long courseId;

    private String courseName;

    /** 学生当前课程均分 */
    private BigDecimal currentScore;

    /** 差距（负数为弱科差距绝对值） */
    private BigDecimal gap;

    /** 优先级 1-5 */
    private Integer priority;
}
