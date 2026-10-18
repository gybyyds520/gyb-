package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.annotation.RequireVip;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.enums.VipFeature;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.service.PortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端门户：学生查看本人学情
 *
 * <p>仅 STUDENT 角色可访问，且所有数据强制取自登录身份绑定的学生，
 * 不接受外部传入的 studentId，杜绝越权查看他人数据。</p>
 */
@Tag(name = "学生端门户")
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portalService;

    @Operation(summary = "我的学情总览")
    @GetMapping("/overview")
    @RequireRole("STUDENT")
    public Result<PortalService.MyOverview> overview(@RequestParam(required = false) Long termId) {
        return Result.success(portalService.overview(termId));
    }

    @Operation(summary = "我的成绩")
    @GetMapping("/scores")
    @RequireRole("STUDENT")
    public Result<List<PortalService.ScoreItem>> scores(@RequestParam(required = false) Long termId) {
        return Result.success(portalService.myScores(termId));
    }

    @Operation(summary = "我的学业指标")
    @GetMapping("/indicator")
    @RequireRole("STUDENT")
    public Result<AcademicIndicatorVO> indicator(@RequestParam(required = false) Long termId) {
        return Result.success(portalService.myIndicator(termId));
    }

    @Operation(summary = "成绩趋势与多学期对比（会员功能）")
    @GetMapping("/score-trend")
    @RequireRole("STUDENT")
    @RequireVip(VipFeature.SCORE_TREND)
    public Result<List<PortalService.TrendItem>> scoreTrend() {
        return Result.success(portalService.scoreTrend());
    }
}
