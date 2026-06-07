package com.hjan.actone.common.context;

import com.hjan.actone.common.exception.ClientException;

public final class UserContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException("未登录或登录已过期");
        }
        return user;
    }

    public static void requireRole(String role) {
        LoginUser user = requireUser();
        if (role == null || !role.equalsIgnoreCase(user.role())) {
            throw new ClientException("权限不足");
        }
    }

    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.userId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.username();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

