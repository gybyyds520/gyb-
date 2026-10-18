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
import com.gyb.xqfx.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 学生 / 家长会员（VIP）
 *
 * <p>教师与各类管理员不参与会员体系，{@link #requireFeature} 对教职工直接放行。</p>
 *
 * <p>支付说明：当前为模拟支付（{@code payMethod=MOCK}），调用支付接口即视为成功并立即开通。
 * 生产环境接入持牌支付渠道时，应以渠道异步回调为准再开通，不可信任前端支付结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipService {

    /** 教师及各类管理员：不参与会员体系 */
    private static final Set<String> STAFF_ROLES =
            Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER");

    private static final int MONTH_DAYS = 30;
    private static final int YEAR_DAYS = 365;

    private final VipLevelMapper levelMapper;
    private final VipOrderMapper orderMapper;
    private final UserVipMapper userVipMapper;

    // ==================== 等级与权益 ====================

    /** 全部可用等级（按次序升序） */
    public List<VipLevel> levels() {
        return levelMapper.selectList(new LambdaQueryWrapper<VipLevel>()
                .eq(VipLevel::getStatus, 1)
                .orderByAsc(VipLevel::getLevelOrder));
    }

    /** 免费与会员的完整功能对照表，供前端「会员中心」展示 */
    public List<FeatureItem> featureMatrix() {
        List<FeatureItem> list = new ArrayList<>();
        for (VipFeature f : VipFeature.values()) {
            FeatureItem item = new FeatureItem();
            item.setCode(f.name());
            item.setLabel(f.getLabel());
            item.setLevel(f.getLevel());
            item.setLevelName(levelName(f.getLevel()));
            list.add(item);
        }
        return list;
    }

    // ==================== 我的会员 ====================

    /** 当前用户会员信息 */
    public MyVip myVip() {
        return myVipOf(SecurityUtils.currentUserId());
    }

    public MyVip myVipOf(Long userId) {
        MyVip v = new MyVip();
        if (userId == null) {
            v.setLevel(0);
            v.setLevelName("普通用户");
            return v;
        }
        UserVip uv = userVipMapper.selectOne(new LambdaQueryWrapper<UserVip>()
                .eq(UserVip::getUserId, userId)
                .eq(UserVip::getStatus, 1)
                .last("LIMIT 1"));
        if (uv == null || uv.getExpireTime() == null || uv.getExpireTime().isBefore(LocalDateTime.now())) {
            v.setLevel(0);
            v.setLevelName("普通用户");
            v.setExpired(uv != null);
            return v;
        }
        v.setLevel(orderOf(uv.getLevelCode()));
        v.setLevelCode(uv.getLevelCode());
        v.setLevelName(levelName(v.getLevel()));
        v.setExpireTime(uv.getExpireTime());
        v.setRemainDays((int) java.time.Duration.between(LocalDateTime.now(), uv.getExpireTime()).toDays());
        v.setTotalAmount(uv.getTotalAmount());
        return v;
    }

    /** 当前用户等级（0 表示普通用户） */
    public int levelOf(Long userId) {
        return myVipOf(userId).getLevel();
    }

    // ==================== 充值 ====================

    /** 下单：生成待支付订单 */
    public VipOrder createOrder(String levelCode, String period) {
        return createOrderFor(SecurityUtils.currentUserId(), levelCode, period);
    }

    public VipOrder createOrderFor(Long userId, String levelCode, String period) {
        VipLevel level = findLevel(levelCode);
        String p = normalizePeriod(period);
        BigDecimal amount = "YEAR".equals(p) ? level.getPriceYear() : level.getPriceMonth();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该等级暂未设置价格");
        }

        VipOrder order = new VipOrder();
        order.setOrderNo("VIP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(1000, 9999));
        order.setUserId(userId);
        order.setLevelCode(level.getLevelCode());
        order.setPeriod(p);
        order.setAmount(amount);
        order.setPayStatus("UNPAID");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setIsDeleted(0);
        orderMapper.insert(order);
        return order;
    }

    /**
     * 支付并开通（模拟支付）
     *
     * @param orderNo 订单号
     * @param payMethod 支付方式，为空默认 MOCK
     */
    @Transactional(rollbackFor = Exception.class)
    public VipOrder payOrder(String orderNo, String payMethod) {
        return payOrderAs(SecurityUtils.currentUserId(), orderNo, payMethod);
    }

    @Transactional(rollbackFor = Exception.class)
    public VipOrder payOrderAs(Long userId, String orderNo, String payMethod) {
        VipOrder order = orderMapper.selectOne(new LambdaQueryWrapper<VipOrder>()
                .eq(VipOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能支付自己的订单");
        }
        if ("PAID".equals(order.getPayStatus())) {
            return order;
        }
        if ("CLOSED".equals(order.getPayStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单已关闭");
        }

        order.setPayStatus("PAID");
        order.setPayMethod(payMethod == null || payMethod.isBlank() ? "MOCK" : payMethod);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        activate(order.getUserId(), order.getLevelCode(), order.getPeriod(), order.getAmount());
        log.info("会员开通成功：userId={} 等级={} 周期={} 金额={}", userId,
                order.getLevelCode(), order.getPeriod(), order.getAmount());
        return order;
    }

    /** 开通或续期：未过期则顺延，已过期则从当前时间重新计算 */
    private void activate(Long userId, String levelCode, String period, BigDecimal amount) {
        int days = "YEAR".equals(period) ? YEAR_DAYS : MONTH_DAYS;
        LocalDateTime now = LocalDateTime.now();
        UserVip uv = userVipMapper.selectOne(new LambdaQueryWrapper<UserVip>()
                .eq(UserVip::getUserId, userId)
                .last("LIMIT 1"));

        if (uv == null) {
            uv = new UserVip();
            uv.setUserId(userId);
            uv.setLevelCode(levelCode);
            uv.setExpireTime(now.plusDays(days));
            uv.setTotalAmount(amount == null ? BigDecimal.ZERO : amount);
            uv.setStatus(1);
            uv.setCreateTime(now);
            uv.setUpdateTime(now);
            uv.setIsDeleted(0);
            userVipMapper.insert(uv);
            return;
        }

        // 仍有效且新等级不低于原等级时顺延，否则从当前时间重新计算
        LocalDateTime base = uv.getExpireTime() != null && uv.getExpireTime().isAfter(now)
                && orderOf(uv.getLevelCode()) <= orderOf(levelCode)
                ? uv.getExpireTime() : now;

        uv.setLevelCode(orderOf(uv.getLevelCode()) > orderOf(levelCode) ? uv.getLevelCode() : levelCode);
        uv.setExpireTime(base.plusDays(days));
        uv.setTotalAmount((uv.getTotalAmount() == null ? BigDecimal.ZERO : uv.getTotalAmount())
                .add(amount == null ? BigDecimal.ZERO : amount));
        uv.setStatus(1);
        uv.setUpdateTime(now);
        userVipMapper.updateById(uv);
    }

    /** 我的订单 */
    public List<VipOrder> myOrders() {
        return orderMapper.selectList(new LambdaQueryWrapper<VipOrder>()
                .eq(VipOrder::getUserId, SecurityUtils.currentUserId())
                .orderByDesc(VipOrder::getCreateTime));
    }

    // ==================== 功能校验 ====================

    /** 当前用户是否可使用该功能（教职工恒为 true） */
    public boolean hasFeature(VipFeature feature) {
        return hasFeature(SecurityUtils.currentUserId(), feature);
    }

    public boolean hasFeature(Long userId, VipFeature feature) {
        if (feature == null || feature.getLevel() <= 0) {
            return true;
        }
        if (isStaff(userId)) {
            return true;
        }
        return levelOf(userId) >= feature.getLevel();
    }

    /** 校验功能权限，不满足时抛出 402 */
    public void requireFeature(VipFeature feature) {
        requireFeature(SecurityUtils.currentUserId(), feature);
    }

    public void requireFeature(Long userId, VipFeature feature) {
        if (hasFeature(userId, feature)) {
            return;
        }
        int need = feature.getLevel();
        throw new BusinessException(ErrorCode.VIP_REQUIRED,
                "「" + feature.getLabel() + "」需要" + levelName(need)
                        + "，当前等级：" + levelName(levelOf(userId)) + "，请先开通会员");
    }

    private boolean isStaff(Long userId) {
        if (userId == null) {
            return false;
        }
        Set<String> roles = SecurityUtils.currentRoles();
        return roles.stream().anyMatch(STAFF_ROLES::contains);
    }

    private VipLevel findLevel(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择会员等级");
        }
        VipLevel level = levelMapper.selectOne(new LambdaQueryWrapper<VipLevel>()
                .eq(VipLevel::getLevelCode, levelCode)
                .eq(VipLevel::getStatus, 1)
                .last("LIMIT 1"));
        if (level == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会员等级不存在：" + levelCode);
        }
        return level;
    }

    private String normalizePeriod(String period) {
        if ("YEAR".equalsIgnoreCase(period)) {
            return "YEAR";
        }
        return "MONTH";
    }

    /** 等级编码 -> 次序，未知返回 0 */
    private int orderOf(String levelCode) {
        if (levelCode == null) {
            return 0;
        }
        return levels().stream()
                .filter(l -> levelCode.equals(l.getLevelCode()))
                .map(l -> l.getLevelOrder() == null ? 0 : l.getLevelOrder())
                .findFirst().orElse(0);
    }

    private String levelName(int level) {
        return switch (level) {
            case 1 -> "VIP1 进阶会员";
            case 2 -> "VIP2 专业会员";
            case 3 -> "VIP3 尊享会员";
            default -> "普通用户";
        };
    }

    // ==================== 内部类 ====================

    /** 我的会员信息 */
    @Data
    public static class MyVip {
        /** 0 普通用户 / 1-3 会员等级 */
        private Integer level;
        private String levelCode;
        private String levelName;
        private LocalDateTime expireTime;
        /** 剩余天数 */
        private Integer remainDays;
        /** 累计充值 */
        private BigDecimal totalAmount;
        /** 是否曾开通但已过期 */
        private Boolean expired;
    }

    /** 功能对照表条目 */
    @Data
    public static class FeatureItem {
        private String code;
        private String label;
        private Integer level;
        private String levelName;
    }
}
