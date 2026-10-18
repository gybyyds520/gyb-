package com.gyb.xqfx.config;

import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.service.DemoDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * 启动初始化测试：空库自动生成演示数据，非空库跳过
 */
@DisplayName("启动初始化测试")
class DataBootstrapRunnerTest {

    @Test
    @DisplayName("空库时自动生成演示数据")
    void generatesWhenEmpty() {
        StudentMapper studentMapper = Mockito.mock(StudentMapper.class);
        DemoDataService demoDataService = Mockito.mock(DemoDataService.class);
        when(studentMapper.selectCount(null)).thenReturn(0L);
        when(demoDataService.generate(100)).thenReturn(Map.of("student", 100));

        DataBootstrapRunner runner = new DataBootstrapRunner(studentMapper, demoDataService);
        ReflectionTestUtils.setField(runner, "autoDemo", true);
        ReflectionTestUtils.setField(runner, "demoCount", 100);

        runner.run(null);

        verify(demoDataService, times(1)).generate(100);
    }

    @Test
    @DisplayName("已有数据时跳过初始化，避免覆盖")
    void skipsWhenNotEmpty() {
        StudentMapper studentMapper = Mockito.mock(StudentMapper.class);
        DemoDataService demoDataService = Mockito.mock(DemoDataService.class);
        when(studentMapper.selectCount(null)).thenReturn(30L);

        DataBootstrapRunner runner = new DataBootstrapRunner(studentMapper, demoDataService);
        ReflectionTestUtils.setField(runner, "autoDemo", true);
        ReflectionTestUtils.setField(runner, "demoCount", 100);

        runner.run(null);

        verify(demoDataService, never()).generate(anyInt());
    }

    @Test
    @DisplayName("开关关闭时不初始化")
    void disabledWhenConfigured() {
        StudentMapper studentMapper = Mockito.mock(StudentMapper.class);
        DemoDataService demoDataService = Mockito.mock(DemoDataService.class);

        DataBootstrapRunner runner = new DataBootstrapRunner(studentMapper, demoDataService);
        ReflectionTestUtils.setField(runner, "autoDemo", false);

        runner.run(null);

        verify(studentMapper, never()).selectCount(any());
        verify(demoDataService, never()).generate(anyInt());
    }
}
