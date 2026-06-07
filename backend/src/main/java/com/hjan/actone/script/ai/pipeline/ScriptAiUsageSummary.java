package com.hjan.actone.script.ai.pipeline;

public record ScriptAiUsageSummary(
        int callCount,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean estimated
) {

    public ScriptAiUsageSummary {
        if (callCount < 0 || promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("AI 用量统计不能小于 0");
        }
    }

    public static ScriptAiUsageSummary empty() {
        return new ScriptAiUsageSummary(0, 0, 0, 0, false);
    }
}
