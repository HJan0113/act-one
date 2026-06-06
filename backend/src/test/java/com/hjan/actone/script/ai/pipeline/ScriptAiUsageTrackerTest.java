package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.ai.model.ChatUsage;
import com.hjan.actone.ai.service.LLMService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAiUsageTrackerTest {

    @Test
    void meteredLlmServiceUsesProviderUsageWhenAvailable() {
        LLMService llmService = ScriptAiMeteredLlmService.wrap(request -> new ChatResponse(
                "完成",
                "model-a",
                "stop",
                new ChatUsage(11, 7, 18),
                "request-1"
        ));

        ScriptAiUsageSummary summary;
        try (ScriptAiUsageTracker ignored = ScriptAiUsageTracker.start()) {
            llmService.chat(request("生成剧本摘要"));
            summary = ScriptAiUsageTracker.currentSummary();
        }

        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.promptTokens()).isEqualTo(11);
        assertThat(summary.completionTokens()).isEqualTo(7);
        assertThat(summary.totalTokens()).isEqualTo(18);
        assertThat(summary.estimated()).isFalse();
    }

    @Test
    void meteredLlmServiceEstimatesTokensWhenProviderUsageIsMissing() {
        LLMService llmService = ScriptAiMeteredLlmService.wrap(request -> ChatResponse.text("完成"));

        ScriptAiUsageSummary summary;
        try (ScriptAiUsageTracker ignored = ScriptAiUsageTracker.start()) {
            llmService.chat(request("生成剧本摘要"));
            summary = ScriptAiUsageTracker.currentSummary();
        }

        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.promptTokens()).isPositive();
        assertThat(summary.completionTokens()).isPositive();
        assertThat(summary.totalTokens()).isEqualTo(summary.promptTokens() + summary.completionTokens());
        assertThat(summary.estimated()).isTrue();
    }

    private ChatRequest request(String prompt) {
        return new ChatRequest(
                List.of(ChatMessage.user(prompt)),
                null,
                0.2D,
                0.8D,
                128,
                Duration.ofSeconds(5),
                ChatResponseFormat.TEXT
        );
    }
}
