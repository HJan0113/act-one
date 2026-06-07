package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;

import java.util.Objects;

public final class ScriptAiMeteredLlmService implements LLMService {

    private final LLMService delegate;

    private ScriptAiMeteredLlmService(LLMService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "LLMService 不能为空");
    }

    public static LLMService wrap(LLMService delegate) {
        if (delegate instanceof ScriptAiMeteredLlmService) {
            return delegate;
        }
        return new ScriptAiMeteredLlmService(delegate);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ChatResponse response = delegate.chat(request);
            ScriptAiUsageTracker.record(request, response);
            return response;
        } catch (RuntimeException ex) {
            ScriptAiUsageTracker.record(request, null);
            throw ex;
        }
    }
}
