package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.UserVip;
import com.gyb.xqfx.entity.VipLevel;
import com.gyb.xqfx.entity.VipOrder;
import com.gyb.xqfx.enums.VipFeature;
import com.gyb.xqfx.mapper.UserVipMapper;
import com.gyb.xqfx.mapper.VipLevelMapper;
import com.gyb.xqfx.mapper.VipOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 会员（VIP）测试：等级、下单支付开通续期、功能门槛
 */
@DisplayName("会员服务测试")
class VipServiceTest {

    private VipLevelMapper levelMapper;
    private VipOrderMapper orderMapper;
    private UserVipMapper userVipMapper;
    private VipService service;

    private static final Long USER = 201L;
    private static final Long OTHER = 999L;

    @BeforeEach
    void setUp() {
        levelMapper = Mockito.mock(VipLevelMapper.class);
        orderMapper = Mockito.mock(VipOrderMapper.class);
        userVipMapper = Mockito.mock(UserVipMapper.class);
        service = new VipService(levelMapper, orderMapper, userVipMapper);
        Mockito.when(levelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(levels());
    }

    private List<VipLevel> levels() {
        return List.of(level("VIP1", "进阶会员", 1, 30, 298),
                level("VIP2", "专业会员", 2, 68, 668),
                level("VIP3", "尊享会员", 3, 128, 1288));
    }

    private VipLevel level(String code, String name, int order, double month, double year) {
        VipLevel l = new VipLevel();
        l.setLevelCode(code);
        l.setName(name);
        l.setLevelOrder(order);
        l.setPriceMonth(BigDecimal.valueOf(month));
        l.setPriceYear(BigDecimal.valueOf(year));
        l.setStatus(1);
        return l;
    }

    private UserVip userVip(Long userId, String levelCode, LocalDateTime expire, BigDecimal total) {
        UserVip v = new UserVip();
        v.setId(1L);
        v.setUserId(userId);
        v.setLevelCode(levelCode);
        v.setExpireTime(expire);
        v.setTotalAmount(total == null ? BigDecimal.ZERO : total);
        v.setStatus(1);
        return v;
    }

    private VipOrder order(String no, Long userId, String levelCode, String period, BigDecimal amount) {
        VipOrder o = new VipOrder();
        o.setId(1L);
        o.setOrderNo(no);
        o.setUserId(userId);
        o.setLevelCode(levelCode);
        o.setPeriod(period);
        o.setAmount(amount);
        o.setPayStatus("UNPAID");
        return o;
    }

    // ==================== 等级与权益 ====================

    @Test
    @DisplayName("等级按次序返回，价格正确")
    void testLevels() {
        List<VipLevel> list = service.levels();
        assertEquals(3, list.size());
        assertEquals("VIP1", list.get(0).getLevelCode());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(list.get(0).getPriceMonth()));
    }

    @Test
    @DisplayName("功能对照表包含免费项与各级会员项")
    void testFeatureMatrix() {
        List<VipService.FeatureItem> items = service.featureMatrix();
        assertTrue(items.stream().anyMatch(i -> "MY_SCORE".equals(i.getCode()) && i.getLevel() == 0),
                "成绩查询应为免费");
        assertTrue(items.stream().anyMatch(i -> "WARNING_VIEW".equals(i.getCode()) && i.getLevel() == 0),
                "学业预警应为免费");
        assertTrue(items.stream().anyMatch(i -> "SCORE_TREND".equals(i.getCode()) && i.getLevel() == 1));
        assertTrue(items.stream().anyMatch(i -> "AI_REPORT".equals(i.getCode()) && i.getLevel() == 2));
        assertTrue(items.stream().anyMatch(i -> "REPORT_EXPORT".equals(i.getCode()) && i.getLevel() == 3));
    }

    // ==================== 我的会员 ====================

    @Test
    @DisplayName("未开通时为普通用户")
    void testNoVip() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        VipService.MyVip v = service.myVipOf(USER);
        assertEquals(0, v.getLevel());
        assertEquals("普通用户", v.getLevelName());
    }

    @Test
    @DisplayName("已过期视为普通用户并标记过期")
    void testExpiredVip() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userVip(USER, "VIP2", LocalDateTime.now().minusDays(1), BigDecimal.valueOf(68)));
        VipService.MyVip v = service.myVipOf(USER);
        assertEquals(0, v.getLevel(), "过期后应为普通用户");
        assertTrue(v.getExpired());
    }

    @Test
    @DisplayName("有效期内返回等级与剩余天数")
    void testActiveVip() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userVip(USER, "VIP2", LocalDateTime.now().plusDays(20), BigDecimal.valueOf(68)));
        VipService.MyVip v = service.myVipOf(USER);
        assertEquals(2, v.getLevel());
        assertEquals("VIP2 专业会员", v.getLevelName());
        assertTrue(v.getRemainDays() >= 19 && v.getRemainDays() <= 20,
                "20 天后到期，剩余天数应为 19~20（跨秒边界）：" + v.getRemainDays());
    }

    // ==================== 下单与支付 ====================

    @Test
    @DisplayName("下单按周期取价：月 30 / 年 298")
    void testCreateOrder() {
        Mockito.when(levelMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(levels().get(0));
        Mockito.doAnswer(inv -> {
            VipOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        }).when(orderMapper).insert(any(VipOrder.class));

        VipOrder month = service.createOrderFor(USER, "VIP1", "MONTH");
        assertEquals(0, BigDecimal.valueOf(30).compareTo(month.getAmount()));
        assertEquals("MONTH", month.getPeriod());
        assertEquals("UNPAID", month.getPayStatus());

        VipOrder year = service.createOrderFor(USER, "VIP1", "YEAR");
        assertEquals(0, BigDecimal.valueOf(298).compareTo(year.getAmount()));
        assertTrue(month.getOrderNo().startsWith("VIP"));
    }

    @Test
    @DisplayName("等级不存在时下单失败")
    void testCreateOrderBadLevel() {
        Mockito.when(levelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createOrderFor(USER, "VIP9", "MONTH"));
    }

    @Test
    @DisplayName("支付后开通：首次开通有效期 30 天")
    void testPayActivate() {
        Mockito.when(orderMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(order("N1", USER, "VIP1", "MONTH", BigDecimal.valueOf(30)));
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        Mockito.doAnswer(inv -> {
            UserVip v = inv.getArgument(0);
            v.setId(1L);
            return 1;
        }).when(userVipMapper).insert(any(UserVip.class));

        LocalDateTime before = LocalDateTime.now().plusDays(29);
        service.payOrderAs(USER, "N1", "MOCK");
        Mockito.verify(userVipMapper).insert(any(UserVip.class));
        // 校验写入的实体（通过 ArgumentCaptor）
        org.mockito.ArgumentCaptor<UserVip> captor = org.mockito.ArgumentCaptor.forClass(UserVip.class);
        Mockito.verify(userVipMapper).insert(captor.capture());
        UserVip saved = captor.getValue();
        assertEquals("VIP1", saved.getLevelCode());
        assertTrue(saved.getExpireTime().isAfter(before), "有效期应为 30 天后");
        assertEquals(0, BigDecimal.valueOf(30).compareTo(saved.getTotalAmount()));
    }

    @Test
    @DisplayName("不能支付他人订单")
    void testPayOtherOrder() {
        Mockito.when(orderMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(order("N1", OTHER, "VIP1", "MONTH", BigDecimal.valueOf(30)));
        assertThrows(BusinessException.class, () -> service.payOrderAs(USER, "N1", "MOCK"));
    }

    @Test
    @DisplayName("重复支付同一订单不重复开通")
    void testPayTwice() {
        Mockito.when(orderMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(order("N1", USER, "VIP1", "MONTH", BigDecimal.valueOf(30)));
        VipOrder paid = order("N1", USER, "VIP1", "MONTH", BigDecimal.valueOf(30));
        paid.setPayStatus("PAID");
        // 第二次调用时订单已是已支付状态
        Mockito.when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paid);

        VipOrder r = service.payOrderAs(USER, "N1", "MOCK");
        assertEquals("PAID", r.getPayStatus());
        Mockito.verify(userVipMapper, Mockito.never()).insert(any(UserVip.class));
    }

    @Test
    @DisplayName("续期：未过期再购买则顺延，累计金额累加")
    void testRenew() {
        LocalDateTime expire = LocalDateTime.now().plusDays(10);
        Mockito.when(orderMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(order("N1", USER, "VIP1", "MONTH", BigDecimal.valueOf(30)));
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userVip(USER, "VIP1", expire, BigDecimal.valueOf(30)));

        service.payOrderAs(USER, "N1", "MOCK");

        org.mockito.ArgumentCaptor<UserVip> captor = org.mockito.ArgumentCaptor.forClass(UserVip.class);
        Mockito.verify(userVipMapper).updateById(captor.capture());
        UserVip updated = captor.getValue();
        assertTrue(updated.getExpireTime().isAfter(expire.plusDays(29)), "应从原到期日顺延 30 天");
        assertEquals(0, BigDecimal.valueOf(60).compareTo(updated.getTotalAmount()), "累计金额应累加");
    }

    @Test
    @DisplayName("升级：VIP1 购买 VIP3 后等级提升")
    void testUpgrade() {
        LocalDateTime expire = LocalDateTime.now().plusDays(10);
        Mockito.when(orderMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(order("N2", USER, "VIP3", "MONTH", BigDecimal.valueOf(128)));
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userVip(USER, "VIP1", expire, BigDecimal.valueOf(30)));

        service.payOrderAs(USER, "N2", "MOCK");

        org.mockito.ArgumentCaptor<UserVip> captor = org.mockito.ArgumentCaptor.forClass(UserVip.class);
        Mockito.verify(userVipMapper).updateById(captor.capture());
        assertEquals("VIP3", captor.getValue().getLevelCode());
    }

    // ==================== 功能门槛 ====================

    @Test
    @DisplayName("免费功能对所有人都开放")
    void testFreeFeature() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertTrue(service.hasFeature(USER, VipFeature.MY_SCORE));
        assertTrue(service.hasFeature(USER, VipFeature.WARNING_VIEW));
        assertTrue(service.hasFeature(USER, VipFeature.MESSAGE));
    }

    @Test
    @DisplayName("普通用户不能使用 VIP1 及以上功能")
    void testBlockedForNormal() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(service.hasFeature(USER, VipFeature.SCORE_TREND));
        assertFalse(service.hasFeature(USER, VipFeature.AI_REPORT));
        assertFalse(service.hasFeature(USER, VipFeature.REPORT_EXPORT));
    }

    @Test
    @DisplayName("高等级自动包含低等级权益")
    void testHigherIncludesLower() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userVip(USER, "VIP3", LocalDateTime.now().plusDays(30), BigDecimal.valueOf(128)));
        assertTrue(service.hasFeature(USER, VipFeature.SCORE_TREND), "VIP3 应包含 VIP1 权益");
        assertTrue(service.hasFeature(USER, VipFeature.AI_REPORT), "VIP3 应包含 VIP2 权益");
        assertTrue(service.hasFeature(USER, VipFeature.REPORT_EXPORT));
    }

    @Test
    @DisplayName("VIP1 不能用 VIP2 功能")
    void testVip1CannotUseVip2() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userVip(USER, "VIP1", LocalDateTime.now().plusDays(30), BigDecimal.valueOf(30)));
        assertTrue(service.hasFeature(USER, VipFeature.SCORE_TREND));
        assertFalse(service.hasFeature(USER, VipFeature.AI_REPORT));
    }

    @Test
    @DisplayName("等级不足时抛出 402 会员错误码")
    void testRequireFeatureThrows() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.requireFeature(USER, VipFeature.AI_REPORT));
        assertEquals(ErrorCode.VIP_REQUIRED.getCode(), e.getCode(), "应返回 402 需要开通会员");
        assertTrue(e.getMessage().contains("AI 学情解读报告"));
    }

    @Test
    @DisplayName("免费功能不触发会员校验")
    void testRequireFreeFeaturePasses() {
        Mockito.when(userVipMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertDoesNotThrow(() -> service.requireFeature(USER, VipFeature.MY_SCORE));
    }
}
