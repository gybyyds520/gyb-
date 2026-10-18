package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学业核心指标视图对象
 *
 * <p>包含高校学业预警的关键量化指标：学分绩点、学分获取情况、
 * 成绩趋势斜率与绩点偏离度，是学业预警规则的事实依据。</p>
 */
@Data
public class AcademicIndicatorVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    /** 学期，为空表示累计 */
    private Long termId;

    private String termName;

    /** 加权平均学分绩点（4.0 制） */
    private BigDecimal gpa;

    /** 计入课程门数 */
    private Integer courseCount;

    /** 修读学分 */
    private BigDecimal totalCredit;

    /** 已获学分（及格课程的学分之和） */
    private BigDecimal earnedCredit;

    /** 欠学分（不及格课程的学分之和） */
    private BigDecimal deficitCredit;

    /** 不及格门数 */
    private Integer failCount;

    /** 学期均分趋势斜率（分/学期，负值表示下滑） */
    private BigDecimal trendSlope;

    /** 最近若干学期的均分序列 */
    private List<BigDecimal> avgScoreSeries;

    /** 绩点偏离度（与同班级/专业均值相差的标准差倍数） */
    private BigDecimal deviation;

    /** 综合学业风险判定：NORMAL正常 / ATTENTION关注 / RISK风险 */
    private String riskFlag;

    /** 判定依据说明 */
    private String reason;
}
