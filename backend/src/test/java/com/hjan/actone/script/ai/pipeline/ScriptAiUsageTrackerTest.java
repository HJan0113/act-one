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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    void bindAllowsWorkerThreadToRecordCurrentTracker() throws Exception {
        LLMService llmService = ScriptAiMeteredLlmService.wrap(request -> new ChatResponse(
                "完成",
                "model-a",
                "stop",
                new ChatUsage(5, 3, 8),
                "request-2"
        ));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        ScriptAiUsageSummary summary;
        try (ScriptAiUsageTracker ignored = ScriptAiUsageTracker.start()) {
            ScriptAiUsageTracker tracker = ScriptAiUsageTracker.currentTracker();
            Future<?> future = executor.submit(() -> {
                try (ScriptAiUsageTracker.Scope ignoredScope = ScriptAiUsageTracker.bind(tracker)) {
                    llmService.chat(request("并发生成剧本摘要"));
                }
            });
            future.get(5, TimeUnit.SECONDS);
            summary = ScriptAiUsageTracker.currentSummary();
        } finally {
            executor.shutdownNow();
        }

        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.promptTokens()).isEqualTo(5);
        assertThat(summary.completionTokens()).isEqualTo(3);
        assertThat(summary.totalTokens()).isEqualTo(8);
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
