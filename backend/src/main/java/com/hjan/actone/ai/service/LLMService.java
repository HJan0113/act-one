package com.hjan.actone.ai.service;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;

public interface LLMService {

    ChatResponse chat(ChatRequest request);

    default ChatResponse chat(String prompt) {
        return chat(ChatRequest.of(prompt));
    }
}
