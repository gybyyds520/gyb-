package com.gyb.xqfx.service;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与功能可见性矩阵
 *
 * <p>本类是「教师 / 学生 / 家长各自能看到什么」的唯一事实来源：
 * 后端接口鉴权、前端菜单渲染、数据范围判定都以此为准，避免三端口径不一致。</p>
 *
 * <p>角色说明：
 * <ul>
 *   <li>ADMIN 系统管理员：全部功能，含系统管理与演示数据</li>
 *   <li>SCHOOL_ADMIN 校级管理者 / COLLEGE_ADMIN 院级管理者：全校或本院学情与治理</li>
 *   <li>COUNSELOR 辅导员 / TEACHER 任课教师：所带班级、所授课程的学情与干预闭环</li>
 *   <li>STUDENT 学生：仅本人学业数据，不含他人名单、干预工单、系统管理</li>
 *   <li>PARENT 家长：仅已绑定子女的学业概况与预警提示，可回执，不含他人与内部流程</li>
 * </ul>
 * </p>
 */
@Service
public class RolePermissionService {

    /** 功能编码常量 */
    public static final String DASHBOARD = "DASHBOARD";
    public static final String STUDENT_LIST = "STUDENT_LIST";
    public static final String SCORE_LIST = "SCORE_LIST";
    public static final String DEMO_DATA = "DEMO_DATA";
    public static final String PREDICTION = "PREDICTION";
    public static final String WARNING = "WARNING";
    public static final String INTERVENTION = "INTERVENTION";
    public static final String AI = "AI";
    public static final String PROFILE = "PROFILE";
    public static final String NOTIFY = "NOTIFY";
    public static final String MESSAGE = "MESSAGE";
    public static final String VIP = "VIP";
    public static final String EXAM = "EXAM";
    public static final String CLAZZ = "CLAZZ";
    public static final String QUALITY = "QUALITY";
    public static final String ACADEMIC = "ACADEMIC";
    public static final String GOVERNANCE = "GOVERNANCE";
    public static final String SYS = "SYS";
    public static final String MY_STUDY = "MY_STUDY";
    public static final String PARENT_PORTAL = "PARENT_PORTAL";
    public static final String GOAL = "GOAL";
    public static final String TODO = "TODO";
    public static final String ACAD = "ACAD";
    public static final String MODEL = "MODEL";
    public static final String AGENT = "AGENT";
    public static final String BIG_SCREEN = "BIG_SCREEN";

    /** 学生端与家长端角色 */
    public static final Set<String> STUDENT_ROLES = Set.of("STUDENT");
    public static final Set<String> PARENT_ROLES = Set.of("PARENT");
    public static final Set<String> TEACHER_ROLES =
            Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER");

    /** 数据范围：全部 / 本学院 / 本班级及授课班 / 仅本人 / 仅子女 */
    public enum Scope { ALL, COLLEGE, CLAZZ, SELF, CHILD }

    /** 功能 → 可见角色 */
    private static final Map<String, Set<String>> MATRIX = new LinkedHashMap<>();

    /** 功能 → 中文名与前端路由，供前端动态菜单使用 */
    private static final Map<String, String[]> MENU = new LinkedHashMap<>();

    static {
        // 全校学情总览属管理视角，学生与家长不开放，其首页为各自的个人门户
        MATRIX.put(DASHBOARD, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(MY_STUDY, Set.of("STUDENT"));
        MATRIX.put(PARENT_PORTAL, Set.of("PARENT"));
        MATRIX.put(STUDENT_LIST, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        // 以下为管理视角功能，学生与家长不开放：其对应诉求由 MY_STUDY / PARENT_PORTAL 承载，
        // 且接口入参 studentId 无法在学生端安全获取，必须隔离
        MATRIX.put(SCORE_LIST, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(DEMO_DATA, Set.of("ADMIN"));
        MATRIX.put(PREDICTION, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(WARNING, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(INTERVENTION, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(AI, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(PROFILE, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(NOTIFY, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN",
                "COUNSELOR", "TEACHER", "STUDENT", "PARENT"));
        // 站内通讯：教师、学生、家长三类角色互通，范围由通讯录关系约束
        MATRIX.put(MESSAGE, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN",
                "COUNSELOR", "TEACHER", "STUDENT", "PARENT"));
        // 会员中心：仅学生与家长，教师端不参与会员体系
        MATRIX.put(VIP, Set.of("STUDENT", "PARENT"));
        MATRIX.put(EXAM, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "TEACHER"));
        MATRIX.put(CLAZZ, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(QUALITY, Set.of("ADMIN"));
        MATRIX.put(ACADEMIC, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        MATRIX.put(GOVERNANCE, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR"));
        MATRIX.put(SYS, Set.of("ADMIN"));
        // 学习目标：学生自我设定，教师/管理者可查看协助；家长不直接参与
        MATRIX.put(GOAL, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"));
        // 待办中心：全员可见，内容按角色聚合
        MATRIX.put(TODO, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN",
                "COUNSELOR", "TEACHER", "STUDENT", "PARENT"));
        // 教务对接：数据接入类，限管理岗
        MATRIX.put(ACAD, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN"));
        // 模型管理：教师可查看评估结果，管理岗可切换部署状态
        MATRIX.put(MODEL, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));
        // 学情智能体：教师与学生的个人学情助手
        MATRIX.put(AGENT, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"));
        // 校级大屏：教学管理与校级驾驶舱
        MATRIX.put(BIG_SCREEN, Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"));

        // code -> { 名称, 路由, 图标分组 }
        MENU.put(DASHBOARD, new String[]{"学情总览", "/dashboard", "main"});
        MENU.put(MY_STUDY, new String[]{"我的学情", "/my", "main"});
        MENU.put(PARENT_PORTAL, new String[]{"家校协同", "/parent", "main"});
        MENU.put(STUDENT_LIST, new String[]{"学生管理", "/data/student", "data"});
        MENU.put(SCORE_LIST, new String[]{"成绩管理", "/data/score", "data"});
        MENU.put(DEMO_DATA, new String[]{"演示数据", "/data/demo", "data"});
        MENU.put(PREDICTION, new String[]{"学业预测", "/prediction", "prediction"});
        MENU.put(WARNING, new String[]{"学业预警", "/warning", "prediction"});
        MENU.put(INTERVENTION, new String[]{"干预闭环", "/intervention", "prediction"});
        MENU.put(AI, new String[]{"AI 学情分析", "/ai", "prediction"});
        MENU.put(PROFILE, new String[]{"学生画像", "/profile", "profile"});
        MENU.put(NOTIFY, new String[]{"通知中心", "/notify", "main"});
        MENU.put(MESSAGE, new String[]{"站内通讯", "/message", "main"});
        MENU.put(VIP, new String[]{"会员中心", "/vip", "main"});
        MENU.put(EXAM, new String[]{"试卷分析", "/exam", "analysis"});
        MENU.put(CLAZZ, new String[]{"班级学情", "/clazz", "analysis"});
        MENU.put(QUALITY, new String[]{"数据质量", "/quality", "analysis"});
        MENU.put(ACADEMIC, new String[]{"学业指标", "/academic", "governance"});
        MENU.put(GOVERNANCE, new String[]{"预警治理", "/governance", "governance"});
        MENU.put(SYS, new String[]{"系统管理", "/sys", "sys"});
        MENU.put(GOAL, new String[]{"学习目标", "/goal", "main"});
        MENU.put(TODO, new String[]{"待办中心", "/todo", "main"});
        MENU.put(ACAD, new String[]{"教务对接", "/acad", "main"});
        MENU.put(MODEL, new String[]{"模型管理", "/model", "main"});
        MENU.put(AGENT, new String[]{"学情智能体", "/agent", "main"});
        MENU.put(BIG_SCREEN, new String[]{"校级大屏", "/bigscreen", "main"});
    }

    /**
     * 角色是否可见某功能
     */
    public boolean canAccess(String userType, String permission) {
        if (permission == null) {
            return false;
        }
        Set<String> roles = MATRIX.get(permission);
        return roles != null && userType != null && roles.contains(userType);
    }

    /**
     * 该角色可见的全部功能（含名称与路由），供前端动态渲染菜单
     */
    public List<Map<String, String>> visibleMenus(String userType) {
        return MENU.entrySet().stream()
                .filter(e -> canAccess(userType, e.getKey()))
                .map(e -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("code", e.getKey());
                    m.put("name", e.getValue()[0]);
                    m.put("path", e.getValue()[1]);
                    m.put("group", e.getValue()[2]);
                    return m;
                })
                .toList();
    }

    /**
     * 该角色的数据范围
     */
    public Scope scopeOf(String userType) {
        if (userType == null) {
            return Scope.SELF;
        }
        return switch (userType) {
            case "ADMIN", "SCHOOL_ADMIN" -> Scope.ALL;
            case "COLLEGE_ADMIN" -> Scope.COLLEGE;
            case "COUNSELOR", "TEACHER" -> Scope.CLAZZ;
            case "PARENT" -> Scope.CHILD;
            default -> Scope.SELF;
        };
    }

    /**
     * 是否为管理端角色（可进入预警处置与治理流程）
     */
    public boolean isStaff(String userType) {
        return userType != null && TEACHER_ROLES.contains(userType);
    }

    /**
     * 完整权限矩阵（用于管理与自查展示）
     */
    public Map<String, Set<String>> fullMatrix() {
        return MATRIX;
    }
}
