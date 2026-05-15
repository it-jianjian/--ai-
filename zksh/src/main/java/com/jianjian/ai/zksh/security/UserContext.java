package com.jianjian.ai.zksh.security;

public final class UserContext {
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();
//基于threadlocal存储用户信息
    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
