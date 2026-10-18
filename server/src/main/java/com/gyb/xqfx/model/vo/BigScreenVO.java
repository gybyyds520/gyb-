package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 校级大屏（驾驶舱）视图对象
 */
@Data
public class BigScreenVO {

    /** 顶部指标带 */
    private Overview overview;

    /** 预警趋势（近 8 周） */
    private List<TrendItem> warningTrend;

    /** 预警等级分布 */
    private List<NameValue> warningLevelDist;

    /** 预警类型分布 */
    private List<NameValue> warningTypeDist;

    /** 学院预警排行 */
    private List<CollegeStat> collegeRanking;

    /** 学院 × 年级 风险热力 */
    private List<HeatCell> riskHeatmap;

    /** 最新待处理预警 */
    private List<WarningItem> latestWarnings;

    /** 最新干预工单 */
    private List<TicketItem> latestTickets;

    @Data
    public static class Overview {
        private Long studentCount;
        private Long activeWarning;
        private Long severeWarning;
        private Long openTicket;
    }

    @Data
    public static class TrendItem {
        /** 周标签，如 09-14 */
        private String week;
        private Long count;
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

    @Data
    public static class CollegeStat {
        private String collegeName;
        private Long warningCount;
        private Long severeCount;
    }

    @Data
    public static class HeatCell {
        private String college;
        private String grade;
        private Long count;
    }

    @Data
    public static class WarningItem {
        private String warningNo;
        private String studentName;
        private String title;
        private Integer warningLevel;
        private String warningType;
        private LocalDateTime createTime;
    }

    @Data
    public static class TicketItem {
        private String ticketNo;
        private String studentName;
        private String title;
        private String status;
        private LocalDateTime createTime;
    }
}
