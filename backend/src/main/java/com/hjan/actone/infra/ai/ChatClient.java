package com.hjan.actone.infra.ai;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;

public interface ChatClient {

    String provider();

    ChatResponse chat(ChatRequest request);
}
