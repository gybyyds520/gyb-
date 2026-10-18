package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 工单时效（SLA）统计视图对象
 *
 * <p>对标行业实践，衡量预警处置的执行力：是否有人真正对预警采取了行动，
 * 用于区分"真正起作用的项目"与"只是产生大量预警的繁忙项目"。</p>
 */
@Data
public class TicketSlaVO {

    /** 工单总数 */
    private Integer totalCount;

    /** 已关闭数 */
    private Integer closedCount;

    /** 超时数 */
    private Integer overdueCount;

    /** 已升级数 */
    private Integer escalatedCount;

    /** 处置及时率（百分比） */
    private BigDecimal onTimeRate;

    /** 结案率（百分比） */
    private BigDecimal closeRate;

    /** 平均处理时长（小时） */
    private BigDecimal avgHandleHours;

    /** 按时长分布 */
    private List<NameValue> overdueDistribution;

    /** 按责任人的处置情况 */
    private List<OwnerStat> ownerStats;

    /** 反馈打标分布 */
    private Map<String, Integer> feedbackDistribution;

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
    public static class OwnerStat {
        private Long ownerUserId;
        private String ownerName;
        private Integer totalCount;
        private Integer overdueCount;
        private Integer closedCount;
        private BigDecimal onTimeRate;
    }
}
