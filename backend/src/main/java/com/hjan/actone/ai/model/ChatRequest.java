package com.hjan.actone.ai.model;

import java.time.Duration;
import java.util.List;

public record ChatRequest(
        List<ChatMessage> messages,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        Duration timeout,
        ChatResponseFormat responseFormat
) {

    public ChatRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }
        if (messages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException("消息列表不能包含空元素");
        }
        messages = List.copyOf(messages);
        model = normalize(model);
        validateRange(temperature, 0D, 2D, "temperature");
        validateRange(topP, 0D, 1D, "topP");
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens 必须大于 0");
        }
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout 必须大于 0");
        }
        responseFormat = responseFormat == null ? ChatResponseFormat.TEXT : responseFormat;
    }

    public static ChatRequest of(String prompt) {
        return of(List.of(ChatMessage.user(prompt)));
    }

    public static ChatRequest of(List<ChatMessage> messages) {
        return new ChatRequest(messages, null, null, null, null, null, ChatResponseFormat.TEXT);
    }

    private static void validateRange(Double value, double min, double max, String field) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(field + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
