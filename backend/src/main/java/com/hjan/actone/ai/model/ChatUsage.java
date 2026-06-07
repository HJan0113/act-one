package com.hjan.actone.ai.model;

public record ChatUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {

    public ChatUsage {
        validateNonNegative(promptTokens, "promptTokens");
        validateNonNegative(completionTokens, "completionTokens");
        validateNonNegative(totalTokens, "totalTokens");
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " 不能小于 0");
        }
    }
}
