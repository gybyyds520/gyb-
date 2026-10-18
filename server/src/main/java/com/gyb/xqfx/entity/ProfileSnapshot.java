package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学生画像快照（五维画像）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("profile_snapshot")
public class ProfileSnapshot extends BaseEntity {

    private Long studentId;

    private Long termId;

    /** 学业水平 0-100 */
    private BigDecimal academicScore;

    /** 学习投入 0-100 */
    private BigDecimal engagementScore;

    /** 行为表现 0-100 */
    private BigDecimal behaviorScore;

    /** 发展潜力 0-100 */
    private BigDecimal potentialScore;

    /** 风险指数 0-100，越大越危险 */
    private BigDecimal riskScore;

    /** 五维明细 JSON */
    private String dimensionJson;

    private LocalDate snapshotDate;
}
