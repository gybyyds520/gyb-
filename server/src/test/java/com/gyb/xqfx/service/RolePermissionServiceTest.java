package com.gyb.xqfx.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 角色功能可见性矩阵测试：明确教师、学生、家长各自能看什么、不能看什么
 */
@DisplayName("角色权限矩阵测试")
class RolePermissionServiceTest {

    private RolePermissionService service;

    @BeforeEach
    void setUp() {
        service = new RolePermissionService();
    }

    // ==================== 教师端 ====================

    @Test
    @DisplayName("教师可进入学情总览、学生名单、预警与干预闭环")
    void testTeacherCoreFunctions() {
        assertTrue(service.canAccess("TEACHER", RolePermissionService.DASHBOARD));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.STUDENT_LIST));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.SCORE_LIST));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.WARNING));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.INTERVENTION));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.PROFILE));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.CLAZZ));
        assertTrue(service.canAccess("TEACHER", RolePermissionService.ACADEMIC));
    }

    @Test
    @DisplayName("教师不能进入系统管理、数据质量与演示数据")
    void testTeacherForbidden() {
        assertFalse(service.canAccess("TEACHER", RolePermissionService.SYS));
        assertFalse(service.canAccess("TEACHER", RolePermissionService.QUALITY));
        assertFalse(service.canAccess("TEACHER", RolePermissionService.DEMO_DATA));
        assertFalse(service.canAccess("TEACHER", RolePermissionService.GOVERNANCE),
                "预警治理（含SLA升级）限辅导员及以上");
    }

    @Test
    @DisplayName("辅导员可进入预警治理，任课教师不可")
    void testGovernanceScope() {
        assertTrue(service.canAccess("COUNSELOR", RolePermissionService.GOVERNANCE));
        assertTrue(service.canAccess("COLLEGE_ADMIN", RolePermissionService.GOVERNANCE));
        assertFalse(service.canAccess("TEACHER", RolePermissionService.GOVERNANCE));
    }

    @Test
    @DisplayName("管理员拥有全部管理功能（学生端/家长端专属页除外）")
    void testAdminAllFunctions() {
        // 学生端/家长端专属页，以及仅面向学生与家长的会员中心
        List<String> personalOnly = List.of(
                RolePermissionService.MY_STUDY, RolePermissionService.PARENT_PORTAL,
                RolePermissionService.VIP);
        for (String code : service.fullMatrix().keySet()) {
            if (personalOnly.contains(code)) {
                assertFalse(service.canAccess("ADMIN", code), "管理员不应有 " + code + " 个人门户");
            } else {
                assertTrue(service.canAccess("ADMIN", code), "管理员应可见 " + code);
            }
        }
    }

    // ==================== 学生端 ====================

    @Test
    @DisplayName("学生只能进入我的学情、通知中心与个人服务功能")
    void testStudentVisible() {
        List<Map<String, String>> menus = service.visibleMenus("STUDENT");
        List<String> codes = menus.stream().map(m -> m.get("code")).toList();
        assertEquals(List.of(RolePermissionService.MY_STUDY,
                RolePermissionService.VIP, RolePermissionService.AGENT,
                RolePermissionService.NOTIFY, RolePermissionService.MESSAGE,
                RolePermissionService.TODO, RolePermissionService.GOAL), codes);
    }

    @Test
    @DisplayName("学生不能查看全校总览、他人名单、干预工单与系统管理")
    void testStudentForbidden() {
        assertFalse(service.canAccess("STUDENT", RolePermissionService.DASHBOARD),
                "学生不应看到全校学情总览");
        assertFalse(service.canAccess("STUDENT", RolePermissionService.STUDENT_LIST));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.INTERVENTION),
                "干预闭环属教师工作流，学生不可见");
        assertFalse(service.canAccess("STUDENT", RolePermissionService.SYS));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.QUALITY));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.PREDICTION));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.PROFILE));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.EXAM));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.CLAZZ));
        assertFalse(service.canAccess("STUDENT", RolePermissionService.PARENT_PORTAL));
    }

    // ==================== 家长端 ====================

    @Test
    @DisplayName("家长只能进入家校协同、通知中心与待办")
    void testParentVisible() {
        List<Map<String, String>> menus = service.visibleMenus("PARENT");
        List<String> codes = menus.stream().map(m -> m.get("code")).toList();
        assertEquals(List.of(RolePermissionService.PARENT_PORTAL,
                RolePermissionService.VIP, RolePermissionService.NOTIFY,
                RolePermissionService.MESSAGE, RolePermissionService.TODO), codes);
    }

    @Test
    @DisplayName("家长不能查看他人、干预工单内部流程与系统管理")
    void testParentForbidden() {
        assertFalse(service.canAccess("PARENT", RolePermissionService.STUDENT_LIST));
        assertFalse(service.canAccess("PARENT", RolePermissionService.INTERVENTION));
        assertFalse(service.canAccess("PARENT", RolePermissionService.SYS));
        assertFalse(service.canAccess("PARENT", RolePermissionService.DASHBOARD));
        assertFalse(service.canAccess("PARENT", RolePermissionService.QUALITY));
        assertFalse(service.canAccess("PARENT", RolePermissionService.EXAM));
        assertFalse(service.canAccess("PARENT", RolePermissionService.MY_STUDY));
        assertFalse(service.canAccess("PARENT", RolePermissionService.PREDICTION));
    }

    // ==================== 三端共性与差异 ====================

    @Test
    @DisplayName("通知中心三端均可进入，但内容各自隔离")
    void testNotifyCommon() {
        assertTrue(service.canAccess("TEACHER", RolePermissionService.NOTIFY));
        assertTrue(service.canAccess("STUDENT", RolePermissionService.NOTIFY));
        assertTrue(service.canAccess("PARENT", RolePermissionService.NOTIFY));
    }

    @Test
    @DisplayName("会员中心仅面向学生与家长，教师端不参与会员体系")
    void testVipOnlyForStudentParent() {
        assertTrue(service.canAccess("STUDENT", RolePermissionService.VIP));
        assertTrue(service.canAccess("PARENT", RolePermissionService.VIP));
        assertFalse(service.canAccess("TEACHER", RolePermissionService.VIP), "教师端不需要会员");
        assertFalse(service.canAccess("ADMIN", RolePermissionService.VIP), "管理员端不需要会员");
    }

    @Test
    @DisplayName("站内通讯三端均可进入（可联系对象由通讯录关系约束）")
    void testMessageCommon() {
        assertTrue(service.canAccess("TEACHER", RolePermissionService.MESSAGE));
        assertTrue(service.canAccess("STUDENT", RolePermissionService.MESSAGE));
        assertTrue(service.canAccess("PARENT", RolePermissionService.MESSAGE));
    }

    @Test
    @DisplayName("教师功能与学生端、家长端完全隔离（公共入口除外）")
    void testTeacherStudentDisjoint() {
        // 公共入口：通知中心、站内通讯、学习目标（两端数据各自隔离）、待办中心（按角色聚合）、学情智能体
        List<String> common = List.of(RolePermissionService.NOTIFY,
                RolePermissionService.MESSAGE, RolePermissionService.VIP,
                RolePermissionService.GOAL, RolePermissionService.TODO,
                RolePermissionService.AGENT);
        List<String> teacher = service.visibleMenus("TEACHER").stream()
                .map(m -> m.get("code"))
                .filter(c -> !common.contains(c)).toList();
        List<String> student = service.visibleMenus("STUDENT").stream()
                .map(m -> m.get("code"))
                .filter(c -> !common.contains(c)).toList();
        List<String> parent = service.visibleMenus("PARENT").stream()
                .map(m -> m.get("code"))
                .filter(c -> !common.contains(c)).toList();

        assertTrue(teacher.stream().noneMatch(student::contains),
                "教师功能不应出现在学生端，交集：" + teacher.stream().filter(student::contains).toList());
        assertTrue(teacher.stream().noneMatch(parent::contains),
                "教师功能不应出现在家长端，交集：" + teacher.stream().filter(parent::contains).toList());
        assertTrue(student.stream().noneMatch(parent::contains),
                "学生端与家长端功能应互斥");
    }

    @Test
    @DisplayName("可见菜单包含名称与路由，前端可直接渲染")
    void testMenuStructure() {
        List<Map<String, String>> menus = service.visibleMenus("ADMIN");
        assertFalse(menus.isEmpty());
        Map<String, String> first = menus.get(0);
        assertTrue(first.containsKey("code"));
        assertTrue(first.containsKey("name"));
        assertTrue(first.containsKey("path"));
        assertTrue(first.get("path").startsWith("/"));
    }

    @Test
    @DisplayName("未知功能编码与未知角色一律拒绝")
    void testUnknown() {
        assertFalse(service.canAccess("STUDENT", "NOT_EXIST"));
        assertFalse(service.canAccess("UNKNOWN_ROLE", RolePermissionService.DASHBOARD));
        assertFalse(service.canAccess(null, RolePermissionService.DASHBOARD));
        assertFalse(service.canAccess("ADMIN", null));
    }

    @Test
    @DisplayName("管理端角色判定")
    void testIsStaff() {
        assertTrue(service.isStaff("TEACHER"));
        assertTrue(service.isStaff("ADMIN"));
        assertFalse(service.isStaff("STUDENT"));
        assertFalse(service.isStaff("PARENT"));
        assertFalse(service.isStaff(null));
    }
}
