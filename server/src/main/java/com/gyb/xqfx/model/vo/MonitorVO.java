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
        private Long attendanceCount;
        private Long behaviorCount;

        /** 最新预测 */
        private List<Item> latestPredictions;
    }

    /** 全量记录通用行 */
    @Data
    public static class RecordRow {
        private Long id;
        private String bizId;
        private String time;
        private String subject;
        private String content;
        private String extra;
    }

    /** 三端账号 */
    @Data
    public static class Account {
        /** 业务主键：学生ID/教师ID/家长账号ID */
        private Long id;
        /** sys_user.id（家长为 parentUserId，学生/教师为其 userId） */
        private Long userId;
        private String no;
        private String name;
        private String extra;
    }

    /** 任意账号数据全景（管理员至高无上权限） */
    @Data
    public static class AccountDetail {
        private Long id;
        private String role;
        private String name;
        private String no;
        private String extra;
        /** 学生端全景（当该账号为 STUDENT 时） */
        private StudentPanorama student;
        /** 教师：名下学生 */
        private List<Item> teacherStudents;
        /** 家长：绑定子女 */
        private List<Item> parentChildren;
        /** 账号相关统计 */
        private AccountStat stat;
    }

    @Data
    public static class AccountStat {
        private Long activeWarning;
        private Long ticketCount;
        private Long feedbackCount;
        private Long notifyUnread;
        private Long messageCount;
        private Long scoreCount;
        private Long predictionCount;
    }
}
