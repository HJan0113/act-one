package com.hjan.actone.user.enums;

public enum UserRole {

    ADMIN("admin"),
    USER("user");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}

