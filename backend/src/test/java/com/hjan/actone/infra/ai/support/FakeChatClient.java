package com.hjan.actone.infra.ai.support;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.infra.ai.ChatClient;

import java.util.ArrayList;
import java.util.List;

public class FakeChatClient implements ChatClient {

    private final List<ChatRequest> requests = new ArrayList<>();
    private ChatResponse response;
    private RuntimeException failure;

    public FakeChatClient responding(ChatResponse response) {
        this.response = response;
        this.failure = null;
        return this;
    }

    public FakeChatClient failing(RuntimeException failure) {
        this.failure = failure;
        this.response = null;
        return this;
    }

    @Override
    public String provider() {
        return "fake";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }
}
