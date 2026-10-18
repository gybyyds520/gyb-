package com.gyb.xqfx.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员功能门槛
 *
 * <p>划分原则：涉及学生切身利益与教育公平的基础功能保持免费
 * （成绩查看、学业预警、家校通讯、通知、回执），
 * 只对增值分析类功能设置会员门槛（趋势分析、薄弱点诊断、AI 解读、报告导出等）。</p>
 *
 * <p>level = 0 表示普通用户可用；1/2/3 分别对应 VIP1/VIP2/VIP3。
 * 高等级自动包含低等级全部权益。</p>
 */
@Getter
@AllArgsConstructor
public enum VipFeature {

    // ===== 免费：基础学业与家校沟通 =====
    MY_OVERVIEW("我的学情概览", 0),
    MY_SCORE("我的成绩查询", 0),
    WARNING_VIEW("学业预警查看", 0),
    MESSAGE("站内通讯（师生家长）", 0),
    NOTIFY("通知中心", 0),
    PARENT_CHILD("子女学业概况", 0),
    FEEDBACK("家长回执确认", 0),

    // ===== VIP1：进阶分析 =====
    SCORE_TREND("成绩趋势与多学期对比", 1),
    WEAKNESS("薄弱知识点诊断", 1),
    PRACTICE("举一反三巩固练习", 1),

    // ===== VIP2：智能解读 =====
    AI_REPORT("AI 学情解读报告", 2),
    PREDICTION("学业走势预测与建议", 2),

    // ===== VIP3：尊享服务 =====
    REPORT_EXPORT("学习报告导出", 3),
    MULTI_CHILD("多子女对比分析", 3);

    private final String label;

    /** 所需会员等级，0 表示免费 */
    private final int level;
}
