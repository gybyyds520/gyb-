package com.gyb.xqfx.aspect;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

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

    @Around("@annotation(requireRole)")
    public Object around(ProceedingJoinPoint point, RequireRole requireRole) throws Throwable {
        String[] roles = requireRole.value();
        if (!SecurityUtils.hasAnyRole(roles)) {
            log.warn("越权访问被拒绝: {} 需要角色 {}", point.getSignature().toShortString(), Arrays.toString(roles));
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前角色无权访问该功能，需要角色：" + Arrays.toString(roles));
        }
        return point.proceed();
    }
}
