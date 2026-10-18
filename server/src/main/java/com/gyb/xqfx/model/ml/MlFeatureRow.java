package com.gyb.xqfx.model.ml;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 机器学习服务的单条样本特征
 *
 * <p>Python 侧使用 snake_case，此处统一命名策略保证双向映射一致。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MlFeatureRow {

    private Long studentId;
    private Long termId;
    private Double avgScore;
    private Double scoreTrend;
    private Double scoreStd;
    private Double minScore;
    private Integer failCount;
    private Double failRate;
    private Double gradePointAvg;
    private Double attendanceRate;
    private Integer lateCount;
    private Integer absentCount;
    private Double avgParticipation;
    private Double avgHomework;
    private Integer questionCount;
    private Double studyMinutesAvg;
    private Double videoProgressAvg;
    private Integer resourceViewCount;
    private Integer forumPostCount;
    private Double assignmentSubmitRate;
    private Double creditTotal;
    private Integer courseCount;
    private Integer label;
}
