package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import com.gyb.xqfx.model.vo.BigScreenVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 校级大屏聚合服务测试：指标带、趋势、分布、排行、热力与最新动态
 */
@DisplayName("校级大屏服务测试")
class BigScreenServiceTest {

    private BigScreenService newService(StudentMapper sm, WarningRecordMapper wm,
                                        InterventionTicketMapper tm, ClazzMapper cm,
                                        MajorMapper mm, CollegeMapper colm) {
        return new BigScreenService(sm, wm, tm, cm, mm, colm);
    }

    private WarningRecord warning(Long id, Long studentId, Integer level, String type, String status,
                                  LocalDateTime createTime) {
        WarningRecord w = new WarningRecord();
        w.setId(id);
        w.setStudentId(studentId);
        w.setWarningNo("W" + id);
        w.setWarningLevel(level);
        w.setWarningType(type);
        w.setStatus(status);
        w.setTitle("预警" + id);
        w.setCreateTime(createTime);
        return w;
    }

    @Test
    @DisplayName("指标带与组织聚合正确")
    void testOverviewAndRanking() {
        StudentMapper sm = Mockito.mock(StudentMapper.class);
        WarningRecordMapper wm = Mockito.mock(WarningRecordMapper.class);
        InterventionTicketMapper tm = Mockito.mock(InterventionTicketMapper.class);
        ClazzMapper cm = Mockito.mock(ClazzMapper.class);
        MajorMapper mm = Mockito.mock(MajorMapper.class);
        CollegeMapper colm = Mockito.mock(CollegeMapper.class);

        when(sm.selectCount(null)).thenReturn(100L);
        when(wm.selectCount(any())).thenReturn(20L, 3L);
        when(tm.selectCount(any())).thenReturn(5L);

        Student s = new Student();
        s.setId(1L);
        s.setName("张三");
        s.setClazzId(11L);
        when(sm.selectList(null)).thenReturn(List.of(s));

        Clazz cz = new Clazz();
        cz.setId(11L);
        cz.setGrade("2023");
        cz.setMajorId(21L);
        when(cm.selectList(null)).thenReturn(List.of(cz));

        Major mj = new Major();
        mj.setId(21L);
        mj.setCollegeId(31L);
        when(mm.selectList(null)).thenReturn(List.of(mj));

        College c = new College();
        c.setId(31L);
        c.setCollegeName("计算与信息科学学院");
        when(colm.selectList(null)).thenReturn(List.of(c));

        LocalDateTime now = LocalDateTime.now();
        when(wm.selectList(any())).thenReturn(List.of(
                warning(1L, 1L, 4, "ACADEMIC", "ACTIVE", now),
                warning(2L, 1L, 2, "ATTENDANCE", "ACTIVE", now.minusDays(1)),
                warning(3L, 1L, 3, "ACADEMIC", "HANDLED", now.minusDays(2))));
        InterventionTicket t = new InterventionTicket();
        t.setTicketNo("TK1");
        t.setStudentId(1L);
        t.setStatus("OPEN");
        t.setPlan("面谈");
        t.setCreateTime(now);
        when(tm.selectList(any())).thenReturn(List.of(t));

        BigScreenService service = newService(sm, wm, tm, cm, mm, colm);
        BigScreenVO vo = service.overview();

        assertEquals(100L, vo.getOverview().getStudentCount());
        assertEquals(20L, vo.getOverview().getActiveWarning());
        assertEquals(3L, vo.getOverview().getSevereWarning());
        assertEquals(5L, vo.getOverview().getOpenTicket());

        // 等级分布：红色1 黄1 橙1（ACTIVE+HANDLED 全量）
        BigScreenVO.NameValue red = vo.getWarningLevelDist().stream()
                .filter(n -> "红色".equals(n.getName())).findFirst().orElse(null);
        assertNotNull(red);
        assertEquals(1L, red.getValue());
        assertEquals(8, vo.getWarningTrend().size());

        // 类型分布：学业 2 > 出勤 1
        assertEquals("学业", vo.getWarningTypeDist().get(0).getName());
        assertEquals(2L, vo.getWarningTypeDist().get(0).getValue());

        // 学院排行与热力（ACTIVE 2 条）
        assertEquals(1, vo.getCollegeRanking().size());
        assertEquals("计算与信息科学学院", vo.getCollegeRanking().get(0).getCollegeName());
        assertEquals(2L, vo.getCollegeRanking().get(0).getWarningCount());
        assertEquals(1L, vo.getCollegeRanking().get(0).getSevereCount());
        assertEquals("2023", vo.getRiskHeatmap().get(0).getGrade());

        // 最新预警：ACTIVE 优先，红色在前
        assertEquals(2, vo.getLatestWarnings().size());
        assertEquals("W1", vo.getLatestWarnings().get(0).getWarningNo());
        assertEquals("张三", vo.getLatestWarnings().get(0).getStudentName());

        // 最新工单
        assertEquals("TK1", vo.getLatestTickets().get(0).getTicketNo());
    }
}
