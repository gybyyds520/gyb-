package com.gyb.xqfx.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.entity.AuditLog;
import com.gyb.xqfx.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志切面：记录标注 {@link Audit} 的操作
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint point, Audit audit) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        try {
            return point.proceed();
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            writeLog(point, audit, (int) (System.currentTimeMillis() - start), success, errorMsg);
        }
    }

    private void writeLog(ProceedingJoinPoint point, Audit audit, int costMs, boolean success, String errorMsg) {
        try {
            AuditLog logEntity = new AuditLog();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                logEntity.setUsername(auth.getName());
            }
            logEntity.setModule(audit.module());
            logEntity.setOperation(audit.operation());
            logEntity.setMethod(point.getSignature().toShortString());
            logEntity.setCostMs(costMs);
            logEntity.setSuccess(success ? 1 : 0);
            logEntity.setErrorMsg(errorMsg);
            logEntity.setCreateTime(LocalDateTime.now());

            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sa) {
                HttpServletRequest request = sa.getRequest();
                logEntity.setUrl(request.getRequestURI());
                logEntity.setIp(request.getRemoteAddr());
                logEntity.setUserAgent(request.getHeader("User-Agent"));
                try {
                    logEntity.setParams(objectMapper.writeValueAsString(request.getParameterMap()));
                } catch (Exception ignored) {
                    // 参数序列化失败不影响主流程
                }
            }
            auditLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("写入审计日志失败：{}", e.getMessage());
        }
    }
}
