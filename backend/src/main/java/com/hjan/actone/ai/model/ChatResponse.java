package com.hjan.actone.ai.model;

public record ChatResponse(
        String content,
        String model,
        String finishReason,
        ChatUsage usage,
        String requestId
) {

    public ChatResponse {
        model = normalize(model);
        finishReason = normalize(finishReason);
        requestId = normalize(requestId);
    }

    public static ChatResponse text(String content) {
        return new ChatResponse(content, null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
