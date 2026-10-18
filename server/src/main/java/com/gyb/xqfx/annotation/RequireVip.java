package com.gyb.xqfx.annotation;

import com.gyb.xqfx.enums.VipFeature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 会员功能鉴权：标注在 Controller 方法上，限定所需会员等级
 *
 * <p>教师与各类管理员不受会员限制；学生、家长等级不足时返回 402。</p>
 *
 * <pre>
 * &#064;RequireVip(VipFeature.AI_REPORT)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireVip {

    /** 所需功能（决定所需等级） */
    VipFeature value();
}
