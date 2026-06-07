package com.hjan.actone.infra.ai.support;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.infra.ai.ChatClient;
import com.hjan.actone.infra.ai.model.ModelTarget;

import java.util.ArrayList;
import java.util.List;

public class FakeChatClient implements ChatClient {

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<ModelTarget> targets = new ArrayList<>();
    private final String provider;
    private ChatResponse response;
    private RuntimeException failure;

    public FakeChatClient() {
        this("fake");
    }

    public FakeChatClient(String provider) {
        this.provider = provider;
    }

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
        return provider;
    }

    @Override
    public ChatResponse chat(ChatRequest request, ModelTarget target) {
        requests.add(request);
        targets.add(target);
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    public List<ModelTarget> targets() {
        return List.copyOf(targets);
    }
}
