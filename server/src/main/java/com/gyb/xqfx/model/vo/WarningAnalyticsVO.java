package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警分析视图对象：多维下钻与学生风险事件时间线
 */
@Data
public class WarningAnalyticsVO {

    @Data
    public static class DrillItem {
        /** 维度值：学院名 / 专业名 / 年级 / 性别 */
        private String name;

        private Integer studentCount;

        private Integer warningCount;

        /** 预警率（百分比） */
        private BigDecimal warningRate;

        private Integer blueCount;
        private Integer yellowCount;
        private Integer orangeCount;
        private Integer redCount;

        /** 已处理数 */
        private Integer handledCount;

        /** 处置率（百分比） */
        private BigDecimal handleRate;
    }

    @Data
    public static class TimelineItem {
        /** 事件时间 */
        private LocalDateTime eventTime;

        /** WARNING预警 / INTERVENTION干预 / SCORE成绩 / ATTENDANCE考勤 */
        private String eventType;

        private String eventTypeText;

        private String title;

        private String detail;

        /** 关联业务ID，便于下钻核查原始数据 */
        private Long refId;

        /** NORMAL正常 / ATTENTION关注 / SEVERE严重 */
        private String severity;
    }

    @Data
    public static class AcademicTrigger {
        /** 是否触发学业风险 */
        private Boolean triggered;

        /** 建议预警等级 1-4 */
        private Integer suggestLevel;

        /** 触发依据 */
        private String reason;

        /** 欠学分 */
        private BigDecimal deficitCredit;

        /** 成绩趋势斜率 */
        private BigDecimal trendSlope;

        /** 绩点偏离度 */
        private BigDecimal deviation;
    }
}
