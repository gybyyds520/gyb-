package com.gyb.xqfx.aspect;

import com.gyb.xqfx.annotation.RequireVip;
import com.gyb.xqfx.service.VipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 会员鉴权切面：拦截 &#064;RequireVip，学生/家长等级不足时拒绝访问
 *
 * <p>与角色鉴权相互独立：先过角色，再过会员。教职工在 {@link VipService} 内直接放行。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequireVipAspect {

    private final VipService vipService;

    @Around("@annotation(requireVip)")
    public Object around(ProceedingJoinPoint point, RequireVip requireVip) throws Throwable {
        try {
            vipService.requireFeature(requireVip.value());
        } catch (RuntimeException e) {
            log.warn("会员权限不足被拒绝: {} 需要 {}", point.getSignature().toShortString(),
                    requireVip.value());
            throw e;
        }
        return point.proceed();
    }
}
