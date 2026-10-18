package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 会员等级
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vip_level")
public class VipLevel extends BaseEntity {

    /** VIP1 / VIP2 / VIP3 */
    private String levelCode;

    private String name;

    /** 等级次序，越大越高 */
    private Integer levelOrder;

    private BigDecimal priceMonth;

    private BigDecimal priceYear;

    /** 权益说明（JSON 数组字符串） */
    private String benefits;

    private Integer status;
}
