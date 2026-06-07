package com.hjan.actone.infra.ai;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.common.log.AiExceptionDiagnostics;
import com.hjan.actone.infra.ai.config.AiModelProperties;
import com.hjan.actone.infra.ai.error.ModelClientExceptionMapper;
import com.hjan.actone.infra.ai.model.ModelHealthStore;
import com.hjan.actone.infra.ai.model.ModelRoutingExecutor;
import com.hjan.actone.infra.ai.model.ModelSelector;
import com.hjan.actone.infra.ai.model.ModelTarget;
import com.hjan.actone.infra.ai.springai.SpringAiProviderIds;
import com.hjan.actone.infra.ai.support.LLMResponseCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

public class DefaultLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLLMService.class);
    private static final String DEFAULT_MODEL = "default";

    private final ModelSelector selector;
    private final ModelRoutingExecutor routingExecutor;
    private final Map<String, ChatClient> clientsByProvider;
    private final LLMResponseCleaner responseCleaner;
    private final ModelClientExceptionMapper exceptionMapper;

    public DefaultLLMService(ChatClient chatClient) {
        Objects.requireNonNull(chatClient, "ChatClient 不能为空");
        AiModelProperties properties = singleClientProperties(chatClient.provider());
        ModelHealthStore healthStore = new ModelHealthStore(properties);
        this.selector = new ModelSelector(properties, healthStore);
        this.routingExecutor = new ModelRoutingExecutor(healthStore);
        this.clientsByProvider = Map.of(chatClient.provider(), chatClient);
        this.responseCleaner = new LLMResponseCleaner();
        this.exceptionMapper = new ModelClientExceptionMapper();
    }

    public DefaultLLMService(ModelSelector selector,
                             ModelRoutingExecutor routingExecutor,
                             List<ChatClient> chatClients,
                             LLMResponseCleaner responseCleaner,
                             ModelClientExceptionMapper exceptionMapper) {
        this.selector = Objects.requireNonNull(selector, "ModelSelector 不能为空");
        this.routingExecutor = Objects.requireNonNull(routingExecutor, "ModelRoutingExecutor 不能为空");
        this.clientsByProvider = Objects.requireNonNull(chatClients, "ChatClient 列表不能为空").stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(ChatClient::provider, Function.identity(), (left, right) -> left));
        this.responseCleaner = Objects.requireNonNull(responseCleaner, "LLMResponseCleaner 不能为空");
        this.exceptionMapper = Objects.requireNonNull(exceptionMapper, "ModelClientExceptionMapper 不能为空");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long startedNanos = System.nanoTime();
        Objects.requireNonNull(request, "ChatRequest 不能为空");
        AtomicReference<ModelTarget> successTarget = new AtomicReference<>();
        ChatResponse response = routingExecutor.executeWithFallback(
                "chat",
                selector.selectChatCandidates(request.model()),
                this::resolveClient,
                (client, target) -> {
                    ChatResponse standardized = invokeClient(client, target, request, startedNanos);
                    successTarget.set(target);
                    return standardized;
                }
        );
        ModelTarget target = successTarget.get();
        String provider = target == null ? "unknown" : safeText(target.candidate().getProvider(), "unknown");
        String requestModel = target == null ? safeText(request.model(), DEFAULT_MODEL) : safeText(target.model(), DEFAULT_MODEL);
        log.info(
                "event=actone_llm_request_success provider={} model={} responseModel={} durationMs={} requestId={} finishReason={} promptTokens={} completionTokens={} totalTokens={}",
                provider,
                requestModel,
                safeText(response.model(), requestModel),
                elapsedMillis(startedNanos),
                safeText(response.requestId(), "n/a"),
                safeText(response.finishReason(), "n/a"),
                response.usage() == null ? null : response.usage().promptTokens(),
                response.usage() == null ? null : response.usage().completionTokens(),
                response.usage() == null ? null : response.usage().totalTokens()
        );
        return response;
    }

    private ChatResponse invokeClient(ChatClient client,
                                      ModelTarget target,
                                      ChatRequest request,
                                      long startedNanos) {
        ChatResponse rawResponse;
        try {
            rawResponse = client.chat(request, target);
        } catch (RemoteException ex) {
            RemoteException mapped = exceptionMapper.map(ex);
            logModelFailure(target, startedNanos, ex, mapped);
            throw mapped;
        } catch (RuntimeException ex) {
            RemoteException mapped = exceptionMapper.map(ex);
            logModelFailure(target, startedNanos, ex, mapped);
            throw mapped;
        }
        try {
            return standardizeResponse(rawResponse);
        } catch (RemoteException ex) {
            logModelFailure(target, startedNanos, ex, ex);
            throw ex;
        }
    }

    private ChatClient resolveClient(ModelTarget target) {
        ChatClient client = clientsByProvider.get(target.candidate().getProvider());
        if (client != null) {
            return client;
        }
        return clientsByProvider.get(SpringAiProviderIds.OPENAI_COMPATIBLE);
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

    private void logModelFailure(ModelTarget target,
                                 long startedNanos,
                                 RuntimeException error,
                                 RemoteException mappedError) {
        log.warn(
                "event=actone_llm_request_failure provider={} model={} durationMs={} errorType={} errorCode={} errorMessage={} modelErrorType={} modelStatus={} rootCauseType={} rootCauseMessage={}",
                target == null ? "unknown" : safeText(target.candidate().getProvider(), "unknown"),
                target == null ? DEFAULT_MODEL : safeText(target.model(), DEFAULT_MODEL),
                elapsedMillis(startedNanos),
                AiExceptionDiagnostics.errorType(error),
                mappedError.getErrorCode(),
                AiExceptionDiagnostics.safeMessage(mappedError),
                AiExceptionDiagnostics.modelErrorType(error),
                AiExceptionDiagnostics.modelStatusCode(error),
                AiExceptionDiagnostics.rootCauseType(error),
                AiExceptionDiagnostics.rootCauseSafeMessage(error)
        );
    }

    private AiModelProperties singleClientProperties(String provider) {
        String providerId = safeText(provider, "fake");
        AiModelProperties.ProviderConfig providerConfig = new AiModelProperties.ProviderConfig();
        providerConfig.setUrl("http://localhost");
        providerConfig.setApiKey("test");

        AiModelProperties.ModelCandidate candidate = new AiModelProperties.ModelCandidate();
        candidate.setId(DEFAULT_MODEL);
        candidate.setProvider(providerId);
        candidate.setModel(DEFAULT_MODEL);
        candidate.setEnabled(true);
        candidate.setPriority(1);

        AiModelProperties properties = new AiModelProperties();
        properties.setProviders(Map.of(providerId, providerConfig));
        properties.getChat().setDefaultModel(DEFAULT_MODEL);
        properties.getChat().setCandidates(List.of(candidate));
        return properties;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }
}
