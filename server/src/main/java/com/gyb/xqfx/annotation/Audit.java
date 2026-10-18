package com.gyb.xqfx.annotation;

import java.lang.annotation.*;

/**
 * 标记需要记录审计日志的操作
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audit {

    /** 业务模块 */
    String module() default "";

    /** 操作描述 */
    String operation() default "";
}
