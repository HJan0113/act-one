package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatUsage;

final class ScriptAiUsageTracker implements AutoCloseable {

    private static final ThreadLocal<ScriptAiUsageTracker> CURRENT = new ThreadLocal<>();

    private int callCount;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private boolean estimated;

    private ScriptAiUsageTracker() {
    }

    static ScriptAiUsageTracker start() {
        ScriptAiUsageTracker tracker = new ScriptAiUsageTracker();
        CURRENT.set(tracker);
        return tracker;
    }

    static ScriptAiUsageTracker currentTracker() {
        return CURRENT.get();
    }

    static Scope bind(ScriptAiUsageTracker tracker) {
        ScriptAiUsageTracker previous = CURRENT.get();
        if (tracker == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tracker);
        }
        return () -> {
            if (previous == null) {
                CURRENT.remove();
                return;
            }
            CURRENT.set(previous);
        };
    }

    static void record(ChatRequest request, ChatResponse response) {
        ScriptAiUsageTracker tracker = CURRENT.get();
        if (tracker != null) {
            tracker.add(request, response);
        }
    }

    static ScriptAiUsageSummary currentSummary() {
        ScriptAiUsageTracker tracker = CURRENT.get();
        return tracker == null ? ScriptAiUsageSummary.empty() : tracker.summary();
    }

    synchronized ScriptAiUsageSummary summary() {
        return new ScriptAiUsageSummary(callCount, promptTokens, completionTokens, totalTokens, estimated);
    }

    @Override
    public void close() {
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
    }

    private synchronized void add(ChatRequest request, ChatResponse response) {
        callCount++;
        ChatUsage usage = response == null ? null : response.usage();
        int nextPromptTokens = usage == null || usage.promptTokens() == null
                ? estimatePromptTokens(request)
                : usage.promptTokens();
        int nextCompletionTokens = usage == null || usage.completionTokens() == null
                ? estimateCompletionTokens(response)
                : usage.completionTokens();
        int nextTotalTokens = usage == null || usage.totalTokens() == null
                ? nextPromptTokens + nextCompletionTokens
                : usage.totalTokens();

        promptTokens += nextPromptTokens;
        completionTokens += nextCompletionTokens;
        totalTokens += nextTotalTokens;
        estimated = estimated
                || usage == null
                || usage.promptTokens() == null
                || usage.completionTokens() == null
                || usage.totalTokens() == null;
    }

    private int estimatePromptTokens(ChatRequest request) {
        if (request == null || request.messages() == null) {
            return 0;
        }
        return request.messages().stream()
                .map(ChatMessage::content)
                .mapToInt(this::estimateTokens)
                .sum();
    }

    private int estimateCompletionTokens(ChatResponse response) {
        return response == null ? 0 : estimateTokens(response.content());
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int codePoints = value.codePointCount(0, value.length());
        return Math.max(1, (int) Math.ceil(codePoints / 2D));
    }

    @FunctionalInterface
    interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
