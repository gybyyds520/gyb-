package com.gyb.xqfx.service.acad;

/**
 * 教务系统 API 对接适配器。
 * <p>
 * 面向"不重建独立数据录入"的毕设答辩场景：本系统与高校教务系统（正方/强智/URP 等）
 * 通过适配器解耦，按类型拉取学生/课程/成绩/考勤主数据。当前提供 Mock 实现
 * （模拟教务系统推送演示数据），接入真实教务系统时新增实现并切换配置
 * {@code xqfx.acad.provider} 即可，接口层不变。
 */
public interface AcadSystemAdapter {

    /** 适配器名称，如 mock / zhengfang / qiangzhi */
    String providerName();

    /** 同步学生主数据 */
    AcadSyncResult syncStudents();

    /** 同步课程主数据 */
    AcadSyncResult syncCourses();

    /** 同步成绩数据 */
    AcadSyncResult syncScores();

    /** 同步考勤数据 */
    AcadSyncResult syncAttendance();
}
