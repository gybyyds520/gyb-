package com.gyb.xqfx.security;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 当前登录用户上下文工具
 *
 * <p>统一从 SecurityContext 读取用户标识、用户类型与角色，
 * 供鉴权切面与数据范围判定使用。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 当前登录用户主体，未登录返回 null
     */
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            return null;
        }
        return user;
    }

    /**
     * 当前用户ID，未登录抛 401
     */
    public static Long currentUserId() {
        LoginUser user = currentUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        return user.getUserId();
    }

    /**
     * 当前用户ID，未登录返回 null（用于非 Web 场景的内部调用）
     */
    public static Long currentUserIdOrNull() {
        LoginUser user = currentUser();
        return user == null ? null : user.getUserId();
    }

    /**
     * 当前用户类型：ADMIN / SCHOOL_ADMIN / COLLEGE_ADMIN / COUNSELOR / TEACHER / STUDENT / PARENT
     */
    public static String currentUserType() {
        LoginUser user = currentUser();
        return user == null ? null : user.getUserType();
    }

    /**
     * 当前用户的全部角色码（不含 ROLE_ 前缀）
     */
    public static Set<String> currentRoles() {
        LoginUser user = currentUser();
        if (user == null) {
            return Set.of();
        }
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }

    /**
     * 是否命中任一指定角色
     */
    public static boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return true;
        }
        Set<String> current = currentRoles();
        String userType = currentUserType();
        for (String r : roles) {
            if (current.contains(r) || (userType != null && userType.equals(r))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 强制要求命中任一角色，否则 403
     */
    public static void requireRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前角色无权访问该功能，需要角色：" + List.of(roles));
        }
    }

    /**
     * 当前登录用户关联的学生ID（学生本人），无则 null
     */
    public static Long currentStudentId() {
        LoginUser user = currentUser();
        return user == null ? null : user.getStudentId();
    }
}
