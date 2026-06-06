package com.hjan.actone.infra.ai;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.infra.ai.model.ModelTarget;

public interface ChatClient {

    String provider();

    ChatResponse chat(ChatRequest request, ModelTarget target);
}
