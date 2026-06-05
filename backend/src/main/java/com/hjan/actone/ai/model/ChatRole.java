package com.hjan.actone.ai.model;

public enum ChatRole {

    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    ChatRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
