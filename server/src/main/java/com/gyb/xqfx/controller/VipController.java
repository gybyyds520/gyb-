package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.VipOrder;
import com.gyb.xqfx.service.VipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会员中心：学生 / 家长充值与权益
 *
 * <p>教师与各类管理员不参与会员体系，因此这里只对学生和家长开放。</p>
 */
@Tag(name = "会员中心")
@RestController
@RequestMapping("/vip")
@RequiredArgsConstructor
public class VipController {

    private final VipService vipService;

    @Operation(summary = "会员等级与价格")
    @GetMapping("/levels")
    @RequireRole({"STUDENT", "PARENT"})
    public Result<List<com.gyb.xqfx.entity.VipLevel>> levels() {
        return Result.success(vipService.levels());
    }

    @Operation(summary = "免费功能与会员功能对照表")
    @GetMapping("/features")
    @RequireRole({"STUDENT", "PARENT"})
    public Result<List<VipService.FeatureItem>> features() {
        return Result.success(vipService.featureMatrix());
    }

    @Operation(summary = "我的会员信息")
    @GetMapping("/my")
    @RequireRole({"STUDENT", "PARENT"})
    public Result<VipService.MyVip> my() {
        return Result.success(vipService.myVip());
    }

    @Operation(summary = "下单（创建待支付订单）")
    @PostMapping("/order")
    @RequireRole({"STUDENT", "PARENT"})
    public Result<VipOrder> createOrder(@RequestParam String levelCode,
                                        @RequestParam(required = false) String period) {
        return Result.success(vipService.createOrder(levelCode, period), "订单已创建");
    }

    @Operation(summary = "支付并开通（模拟支付）")
    @PostMapping("/pay")
    @RequireRole({"STUDENT", "PARENT"})
    public Result<Map<String, Object>> pay(@RequestParam String orderNo,
                                           @RequestParam(required = false) String payMethod) {
        VipOrder order = vipService.payOrder(orderNo, payMethod);
        VipService.MyVip vip = vipService.myVip();
        return Result.success(Map.of(
                "orderNo", order.getOrderNo(),
                "amount", order.getAmount(),
                "levelCode", order.getLevelCode(),
                "level", vip.getLevel(),
                "levelName", vip.getLevelName(),
                "expireTime", vip.getExpireTime() == null ? "" : vip.getExpireTime().toString()
        ), "开通成功，有效期至 " + (vip.getExpireTime() == null ? "-" : vip.getExpireTime()));
    }

    @Operation(summary = "我的充值订单")
    @GetMapping("/orders")
    @RequireRole({"STUDENT", "PARENT"})
    public Result<List<VipOrder>> orders() {
        return Result.success(vipService.myOrders());
    }
}
