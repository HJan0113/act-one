package com.hjan.actone.infra.ai;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.error.ModelClientExceptionMapper;
import com.hjan.actone.infra.ai.support.LLMResponseCleaner;

import java.util.Objects;

public class DefaultLLMService implements LLMService {

    private final ChatClient chatClient;
    private final LLMResponseCleaner responseCleaner;
    private final ModelClientExceptionMapper exceptionMapper;

    public DefaultLLMService(ChatClient chatClient) {
        this(chatClient, new LLMResponseCleaner(), new ModelClientExceptionMapper());
    }

    public DefaultLLMService(ChatClient chatClient,
                             LLMResponseCleaner responseCleaner,
                             ModelClientExceptionMapper exceptionMapper) {
        this.chatClient = Objects.requireNonNull(chatClient, "ChatClient 不能为空");
        this.responseCleaner = Objects.requireNonNull(responseCleaner, "LLMResponseCleaner 不能为空");
        this.exceptionMapper = Objects.requireNonNull(exceptionMapper, "ModelClientExceptionMapper 不能为空");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "ChatRequest 不能为空");

        ChatResponse response;
        try {
            response = chatClient.chat(request);
        } catch (RuntimeException ex) {
            throw exceptionMapper.map(ex);
        }
        if (response == null) {
            throw new RemoteException("AI 模型返回内容为空", BaseErrorCode.REMOTE_ERROR);
        }
        String cleanedContent = responseCleaner.clean(response.content());
        if (cleanedContent == null || cleanedContent.isBlank()) {
            throw new RemoteException("AI 模型返回内容为空", BaseErrorCode.REMOTE_ERROR);
        }
        return new ChatResponse(
                cleanedContent,
                response.model(),
                response.finishReason(),
                response.usage(),
                response.requestId()
        );
    }
}
