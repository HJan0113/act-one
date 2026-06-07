package com.hjan.actone.ai.model;

import java.util.Objects;

public record ChatMessage(
        ChatRole role,
        String content
) {

    public ChatMessage {
        Objects.requireNonNull(role, "消息角色不能为空");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content);
    }
}
