package com.gyb.xqfx.aspect;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色鉴权切面：拦截 &#064;RequireRole，未命中角色直接 403
 *
 * <p>教师、学生、家长三端的功能隔离在接口层强制执行，
 * 即使前端绕过菜单直接调用接口也无法越权。</p>
 */
@Slf4j
@Aspect
@Component
public class RequireRoleAspect {

    /**
     * 同时支持方法级与类级注解（限定在本项目 controller 包，避免误伤 Spring 内部 Bean）。
     * 切点用 @target（目标对象类注解，兼容 CGLIB 代理）+ @annotation（方法注解）；
     * 实际角色集合在切面内通过反射读取：方法注解优先，未命中回退类注解。
     */
    @Around("execution(* com.gyb.xqfx.controller..*.*(..)) && "
            + "(@target(com.gyb.xqfx.annotation.RequireRole) || @annotation(com.gyb.xqfx.annotation.RequireRole))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature sig = (MethodSignature) point.getSignature();
        Method method = sig.getMethod();
        RequireRole role = method.getAnnotation(RequireRole.class);
        if (role == null) {
            Class<?> declaringType = method.getDeclaringClass();
            role = declaringType.getAnnotation(RequireRole.class);
        }
        if (role == null || role.value().length == 0) {
            // 无注解或注解无角色：不拦截（保持默认行为）
            return point.proceed();
        }
        String[] roles = role.value();
        if (!SecurityUtils.hasAnyRole(roles)) {
            log.warn("越权访问被拒绝: {} 需要角色 {}", point.getSignature().toShortString(), Arrays.toString(roles));
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前角色无权访问该功能，需要角色：" + Arrays.toString(roles));
        }
        return point.proceed();
    }
}
