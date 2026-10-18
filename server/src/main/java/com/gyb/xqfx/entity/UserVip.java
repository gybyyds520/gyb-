package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户会员（学生 / 家长）
 *
 * <p>一人一条记录；等级按 levelCode 比较，到期后降级为普通用户。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_vip")
public class UserVip extends BaseEntity {

    private Long userId;

    /** VIP1 / VIP2 / VIP3 */
    private String levelCode;

    private LocalDateTime expireTime;

    /** 累计充值金额 */
    private BigDecimal totalAmount;

    /** 1 有效 / 0 失效 */
    private Integer status;
}
