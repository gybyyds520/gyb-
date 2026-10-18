package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员充值订单
 *
 * <p>当前为模拟支付（pay_method=MOCK）：下单后调用支付接口即视为支付成功并开通。
 * 生产环境接入持牌支付渠道时，应改为以支付渠道异步回调为准再开通。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vip_order")
public class VipOrder extends BaseEntity {

    private String orderNo;

    private Long userId;

    private String levelCode;

    /** MONTH / YEAR */
    private String period;

    private BigDecimal amount;

    /** UNPAID 待支付 / PAID 已支付 / CLOSED 已关闭 */
    private String payStatus;

    /** MOCK 模拟 / WECHAT / ALIPAY */
    private String payMethod;

    private LocalDateTime payTime;
}
