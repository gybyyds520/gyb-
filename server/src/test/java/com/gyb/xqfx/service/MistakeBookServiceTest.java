package com.gyb.xqfx.service;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.MistakeBook;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.MistakeBookMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 错题本服务测试（Mapper / 数据范围使用 Mockito 隔离）
 */
@DisplayName("错题本服务测试")
class MistakeBookServiceTest {

    private MistakeBookMapper mistakeBookMapper;
    private StudentMapper studentMapper;
    private DataScopeService dataScopeService;
    private MistakeBookService service;

    @BeforeEach
    void setUp() {
        mistakeBookMapper = Mockito.mock(MistakeBookMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        dataScopeService = Mockito.mock(DataScopeService.class);
        service = new MistakeBookService(mistakeBookMapper, studentMapper, dataScopeService);
    }

    private MistakeBook book(Long id, Long studentId, String course, String point, String status) {
        MistakeBook b = new MistakeBook();
        b.setId(id);
        b.setStudentId(studentId);
        b.setCourseName(course);
        b.setKnowledgePoint(point);
        b.setStatus(status);
        return b;
    }

    @Test
    @DisplayName("手动收录错题成功")
    void add_ok() {
        when(mistakeBookMapper.selectCount(any())).thenReturn(0L);
        when(mistakeBookMapper.insert(any(MistakeBook.class))).thenAnswer(inv -> {
            ((MistakeBook) inv.getArgument(0)).setId(10L);
            return 1;
        });
        Long id = service.add(1L, 2L, "高等数学", "极限", "CHOICE",
                "1+1=?", "3", "2", "MANUAL", null, "概念不清");
        assertEquals(10L, id);
        verify(mistakeBookMapper).insert(any(MistakeBook.class));
    }

    @Test
    @DisplayName("同一错题重复收录自动跳过（作业批量场景去重）")
    void add_dup_skip() {
        when(mistakeBookMapper.selectCount(any())).thenReturn(1L);
        Long id = service.add(1L, 2L, "高数", null, "CHOICE",
                "1+1=?", "3", "2", "HOMEWORK", 99L, null);
        assertNull(id);
        verify(mistakeBookMapper, never()).insert(any(MistakeBook.class));
    }

    @Test
    @DisplayName("缺少学生时拒绝收录")
    void add_noStudent() {
        assertThrows(BusinessException.class,
                () -> service.add(null, null, "高数", null, "CHOICE",
                        "题目", "错", "对", "MANUAL", null, null));
    }

    @Test
    @DisplayName("本人错题本按课程分组，未掌握数量正确")
    void myList_group() {
        when(mistakeBookMapper.selectList(any())).thenReturn(List.of(
                book(1L, 1L, "高数", "极限", "UNRESOLVED"),
                book(2L, 1L, "高数", "导数", "UNRESOLVED"),
                book(3L, 1L, "英语", "阅读", "MASTERED")));
        List<Map<String, Object>> groups = service.myList(1L);
        assertEquals(2, groups.size());
        Map<String, Object> math = groups.get(0);
        assertEquals("高数", math.get("courseName"));
        assertEquals(2, math.get("total"));
        assertEquals(2L, math.get("unresolved"));
        Map<String, Object> eng = groups.get(1);
        assertEquals("英语", eng.get("courseName"));
        assertEquals(1, eng.get("total"));
        assertEquals(0L, eng.get("unresolved"));
    }

    @Test
    @DisplayName("错题统计：总量/未掌握/知识点/错因")
    void stats() {
        when(mistakeBookMapper.selectList(any())).thenReturn(List.of(
                book(1L, 1L, "高数", "极限", "UNRESOLVED"),
                book(2L, 1L, "高数", "极限", "MASTERED"),
                book(3L, 1L, "英语", "阅读", "UNRESOLVED")));
        Map<String, Object> stats = service.stats(1L);
        assertEquals(3, stats.get("total"));
        assertEquals(2L, stats.get("unresolved"));
        assertEquals(1L, stats.get("mastered"));
        Map<String, Long> points = (Map<String, Long>) stats.get("knowledgePoints");
        assertEquals(2L, points.get("极限"));
        assertEquals(1L, points.get("阅读"));
    }

    @Test
    @DisplayName("标记掌握需要数据范围校验")
    void markMastered_scope() {
        MistakeBook b = book(5L, 1L, "高数", "极限", "UNRESOLVED");
        when(mistakeBookMapper.selectById(5L)).thenReturn(b);
        doNothing().when(dataScopeService).requireStudentAccess(1L);
        service.markMastered(5L);
        assertEquals("MASTERED", b.getStatus());
        verify(mistakeBookMapper).updateById(b);
    }

    @Test
    @DisplayName("删除不存在错题报错")
    void remove_missing() {
        when(mistakeBookMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.remove(9L));
    }

    @Test
    @DisplayName("教师查看学生错题含学生姓名")
    void listOfStudent() {
        Student s = new Student();
        s.setId(1L);
        s.setName("张三");
        when(studentMapper.selectById(1L)).thenReturn(s);
        when(mistakeBookMapper.selectList(any())).thenReturn(List.of(
                book(1L, 1L, "高数", "极限", "UNRESOLVED")));
        List<Map<String, Object>> rows = service.listOfStudent(1L);
        assertEquals(1, rows.size());
        assertEquals("张三", rows.get(0).get("studentName"));
        verify(dataScopeService).requireStudentAccess(1L);
    }
}
