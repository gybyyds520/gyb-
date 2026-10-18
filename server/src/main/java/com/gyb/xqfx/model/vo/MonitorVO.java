package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员监察中心视图对象
 */
@Data
public class MonitorVO {

    /** 三端 + 系统概览 */
    private Overview overview;

    /** 各端最新动态 */
    private Latest latest;

    /** 指定学生数据全景 */
    private StudentPanorama student;

    @Data
    public static class Overview {
        private StudentStat student;
        private TeacherStat teacher;
        private ParentStat parent;
        private SystemStat system;
    }

    @Data
    public static class StudentStat {
        private Long studentCount;
        private Long goalCount;
        private Long profileCount;
        private Long unreadNotify;
        private Long messageCount;
    }

    @Data
    public static class TeacherStat {
        private Long teacherCount;
        private Long activeWarning;
        private Long openTicket;
        private Long snapshotCount;
    }

    @Data
    public static class ParentStat {
        private Long parentCount;
        private Long feedbackCount;
        private Long unreadNotify;
    }

    @Data
    public static class SystemStat {
        private Long userCount;
        private Long auditCount;
        private Long aiCallCount;
        private Long scoreCount;
        private Long predictionCount;
    }

    @Data
    public static class Latest {
        private List<Item> warnings;
        private List<Item> tickets;
        private List<Item> feedbacks;
        private List<Item> messages;
        private List<Item> aiCalls;
        private List<Item> audits;
    }

    @Data
    public static class Item {
        private String time;
        private String subject;
        private String content;
        private String extra;
    }

    @Data
    public static class StudentPanorama {
        private Long studentId;
        private String studentNo;
        private String studentName;
        private String clazzName;
        private String collegeName;
        private String grade;

        /** 成绩概况 */
        private Long scoreCount;
        private BigDecimal avgScore;

        /** 画像/预警/目标/待办/消息/工单 */
        private Long snapshotCount;
        private Long activeWarning;
        private Long goalCount;
        private Long notifyUnread;
        private Long messageCount;
        private Long ticketCount;

        /** 最新预测 */
        private List<Item> latestPredictions;
    }
}
