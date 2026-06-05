package com.hjan.actone.infra.ai;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;

import java.util.Objects;

public class DefaultLLMService implements LLMService {

    private final ChatClient chatClient;

    public DefaultLLMService(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "ChatClient 不能为空");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "ChatRequest 不能为空");

        ChatResponse response;
        try {
            response = chatClient.chat(request);
        } catch (RemoteException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RemoteException("AI 模型调用失败，请稍后重试", ex, BaseErrorCode.REMOTE_ERROR);
        }
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new RemoteException("AI 模型返回内容为空", BaseErrorCode.REMOTE_ERROR);
        }
        return response;
    }
}
