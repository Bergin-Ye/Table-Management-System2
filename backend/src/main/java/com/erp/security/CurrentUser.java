package com.erp.security;

import com.erp.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户读取工具。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static LoginUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw BizException.unauthorized("未登录");
    }

    public static Long id() {
        return get().getId();
    }

    public static String username() {
        return get().getUsername();
    }

    public static String role() {
        return get().getRole();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role());
    }
}
