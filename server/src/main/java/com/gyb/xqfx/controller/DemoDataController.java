package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.service.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 演示数据生成（毕设演示与测试用）
 */
@Tag(name = "演示数据")
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoDataService demoDataService;

    @Operation(summary = "生成演示数据")
    @PostMapping("/generate")
    @RequireRole({"ADMIN"})
    public Result<Map<String, Object>> generate(@RequestParam(defaultValue = "300") int studentCount) {
        if (studentCount < 1 || studentCount > 5000) {
            return Result.fail("学生数量应在 1~5000 之间");
        }
        return Result.success(demoDataService.generate(studentCount), "演示数据生成成功");
    }

    @Operation(summary = "清空演示数据")
    @DeleteMapping("/clear")
    @RequireRole({"ADMIN"})
    public Result<Boolean> clear() {
        demoDataService.clear();
        return Result.success(true, "演示数据已清空");
    }
}
