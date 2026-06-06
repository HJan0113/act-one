package com.hjan.actone.infra.ai;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.error.ModelClientExceptionMapper;
import com.hjan.actone.infra.ai.support.LLMResponseCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DefaultLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLLMService.class);
    private static final String DEFAULT_MODEL = "default";
    private static final int DEFAULT_RETRY = 0;

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
        long startedNanos = System.nanoTime();
        Objects.requireNonNull(request, "ChatRequest 不能为空");
        String provider = safeText(chatClient.provider(), "unknown");
        String requestModel = safeText(request.model(), DEFAULT_MODEL);

        ChatResponse rawResponse;
        try {
            rawResponse = chatClient.chat(request);
        } catch (RuntimeException ex) {
            RemoteException mapped = exceptionMapper.map(ex);
            logModelFailure(provider, requestModel, startedNanos, ex.getClass().getSimpleName(), mapped.getErrorCode());
            throw mapped;
        }
        ChatResponse response;
        try {
            response = standardizeResponse(rawResponse);
        } catch (RemoteException ex) {
            logModelFailure(provider, requestModel, startedNanos, ex.getClass().getSimpleName(), ex.getErrorCode());
            throw ex;
        }
        log.info(
                "event=actone_llm_request_success provider={} model={} responseModel={} retry={} durationMs={} requestId={} finishReason={} promptTokens={} completionTokens={} totalTokens={}",
                provider,
                requestModel,
                safeText(response.model(), requestModel),
                DEFAULT_RETRY,
                elapsedMillis(startedNanos),
                safeText(response.requestId(), "n/a"),
                safeText(response.finishReason(), "n/a"),
                response.usage() == null ? null : response.usage().promptTokens(),
                response.usage() == null ? null : response.usage().completionTokens(),
                response.usage() == null ? null : response.usage().totalTokens()
        );
        return response;
    }

    private ChatResponse standardizeResponse(ChatResponse response) {
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

    private void logModelFailure(String provider,
                                 String requestModel,
                                 long startedNanos,
                                 String errorType,
                                 String errorCode) {
        log.warn(
                "event=actone_llm_request_failure provider={} model={} retry={} durationMs={} errorType={} errorCode={}",
                provider,
                requestModel,
                DEFAULT_RETRY,
                elapsedMillis(startedNanos),
                errorType,
                errorCode
        );
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }
}
