package com.gyb.xqfx.service.acad;

import com.gyb.xqfx.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 教务系统 Mock 适配器：模拟教务平台推送主数据。
 * <p>
 * 实际接入时新增实现类（如 ZhengfangAcadSystemAdapter）并在
 * {@code application.yml} 中将 {@code xqfx.acad.provider} 切换为对应标识，
 * 接口与控制器无需改动。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "xqfx.acad", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAcadSystemAdapter implements AcadSystemAdapter {

    private final DemoDataService demoDataService;

    @Value("${xqfx.acad.mock-batch:50}")
    private int mockBatch;

    @Override
    public String providerName() {
        return "mock";
    }

    private Map<String, Object> pull() {
        return demoDataService.generate(mockBatch);
    }

    @Override
    public AcadSyncResult syncStudents() {
        Map<String, Object> summary = pull();
        int n = asInt(summary.get("student"));
        return AcadSyncResult.ok(providerName(), "STUDENTS", n, n,
                "模拟教务系统推送学生主数据，新增/已存在 " + n + " 条（幂等）");
    }

    @Override
    public AcadSyncResult syncCourses() {
        Map<String, Object> summary = pull();
        int n = asInt(summary.get("course"));
        return AcadSyncResult.ok(providerName(), "COURSES", n, n,
                "模拟教务系统推送课程主数据，课程 " + n + " 门（含开课计划）");
    }

    @Override
    public AcadSyncResult syncScores() {
        Map<String, Object> summary = pull();
        int n = asInt(summary.get("score"));
        return AcadSyncResult.ok(providerName(), "SCORES", n, n,
                "模拟教务系统推送成绩数据，" + n + " 条");
    }

    @Override
    public AcadSyncResult syncAttendance() {
        Map<String, Object> summary = pull();
        int n = asInt(summary.get("attendance"));
        return AcadSyncResult.ok(providerName(), "ATTENDANCE", n, n,
                "模拟教务系统推送考勤数据，" + n + " 条");
    }

    private int asInt(Object o) {
        return o instanceof Number num ? num.intValue() : 0;
    }
}
