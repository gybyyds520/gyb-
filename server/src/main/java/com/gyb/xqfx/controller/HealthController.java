package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查与系统信息
 */
@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "xqfx-server");
        info.put("status", "UP");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("timestamp", System.currentTimeMillis());
        return Result.success(info);
    }
}
