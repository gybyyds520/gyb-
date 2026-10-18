package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.model.vo.BigScreenVO;
import com.gyb.xqfx.service.BigScreenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校级大屏（学情总览驾驶舱）
 */
@Tag(name = "校级大屏")
@RestController
@RequestMapping("/bigscreen")
@RequiredArgsConstructor
public class BigScreenController {

    private final BigScreenService bigScreenService;

    @Operation(summary = "大屏全量聚合数据")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @GetMapping("/overview")
    public Result<BigScreenVO> overview() {
        return Result.success(bigScreenService.overview());
    }
}
