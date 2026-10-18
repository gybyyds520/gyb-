package com.gyb.xqfx.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色鉴权：标注在 Controller 方法或类上，限定允许访问的用户类型
 *
 * <p>命中其中任意一个角色即可访问；未命中返回 403。
 * 方法级注解优先于类级注解；标注在类上时对该类全部接口生效。</p>
 *
 * <pre>
 * &#064;RequireRole({"ADMIN", "SCHOOL_ADMIN"})
 * &#064;RequireRole("ADMIN")   // 单角色简写
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许的用户类型（与 sys_user.user_type / 角色码一致） */
    String[] value();
}
