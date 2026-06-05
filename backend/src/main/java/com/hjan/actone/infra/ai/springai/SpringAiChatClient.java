package com.hjan.actone.infra.ai.springai;

import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.ai.model.ChatUsage;
import com.hjan.actone.infra.ai.ChatClient;
import com.hjan.actone.infra.ai.config.AiModelProperties;
import com.hjan.actone.infra.ai.error.ModelClientErrorType;
import com.hjan.actone.infra.ai.error.ModelClientException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class SpringAiChatClient implements ChatClient {

    private static final String DEFAULT_EMBEDDINGS_PATH = "/v1/embeddings";

    private final AiModelProperties properties;
    private final Function<Duration, RestClient.Builder> restClientBuilderFactory;

    public SpringAiChatClient(AiModelProperties properties) {
        this(properties, SpringAiChatClient::newRestClientBuilder);
    }

    SpringAiChatClient(AiModelProperties properties,
                       Function<Duration, RestClient.Builder> restClientBuilderFactory) {
        this.properties = Objects.requireNonNull(properties, "Spring AI 配置不能为空");
        this.restClientBuilderFactory = Objects.requireNonNull(
                restClientBuilderFactory,
                "RestClient.Builder 工厂不能为空"
        );
    }

    @Override
    public String provider() {
        return "spring-ai-openai-compatible";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "ChatRequest 不能为空");
        try {
            ModelTarget target = resolveTarget(request);
            String model = target.model();
            OpenAiChatOptions options = createOptions(request, model);
            OpenAiChatModel chatModel = createModel(target, resolveTimeout(request), options);
            org.springframework.ai.chat.model.ChatResponse response = org.springframework.ai.chat.client.ChatClient
                    .create(chatModel)
                    .prompt(new Prompt(toMessages(request), options))
                    .call()
                    .chatResponse();
            return toResponse(response, model);
        } catch (ModelClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapException(ex);
        }
    }

    private OpenAiChatModel createModel(ModelTarget target,
                                        Duration timeout,
                                        OpenAiChatOptions options) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(requireText(target.provider().getUrl(), "Spring AI provider url 未配置"))
                .apiKey(requireText(target.provider().getApiKey(), "Spring AI provider apiKey 未配置"))
                .completionsPath(resolveEndpoint(target.provider(), "chat", "/v1/chat/completions"))
                .embeddingsPath(resolveEndpoint(target.provider(), "embedding", DEFAULT_EMBEDDINGS_PATH))
                .restClientBuilder(restClientBuilderFactory.apply(timeout))
                .responseErrorHandler(new ModelResponseErrorHandler())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .retryTemplate(noRetryTemplate())
                .build();
    }

    private OpenAiChatOptions createOptions(ChatRequest request, String model) {
        AiModelProperties.ChatSettings settings = properties.getSpringAi().getChat();
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(request.temperature() == null ? settings.getTemperature() : request.temperature())
                .topP(request.topP() == null ? settings.getTopP() : request.topP())
                .maxTokens(request.maxTokens() == null ? settings.getMaxTokens() : request.maxTokens());
        if (request.responseFormat() == ChatResponseFormat.JSON_OBJECT) {
            builder.responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null));
        }
        return builder.build();
    }

    private List<Message> toMessages(ChatRequest request) {
        List<Message> messages = new ArrayList<>(request.messages().size());
        for (ChatMessage message : request.messages()) {
            messages.add(switch (message.role()) {
                case SYSTEM -> new SystemMessage(message.content());
                case USER -> new UserMessage(message.content());
                case ASSISTANT -> new AssistantMessage(message.content());
            });
        }
        return messages;
    }

    private ChatResponse toResponse(org.springframework.ai.chat.model.ChatResponse response,
                                    String requestedModel) {
        if (response == null || response.getResult() == null) {
            throw invalidResponse("Spring AI 模型响应为空", null);
        }
        Generation generation = response.getResult();
        if (generation.getOutput() == null || !StringUtils.hasText(generation.getOutput().getText())) {
            throw invalidResponse("Spring AI 模型返回内容为空", null);
        }

        ChatResponseMetadata metadata = response.getMetadata();
        String model = metadata == null ? null : metadata.getModel();
        String requestId = metadata == null ? null : metadata.getId();
        String finishReason = generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();
        return new ChatResponse(
                generation.getOutput().getText(),
                StringUtils.hasText(model) ? model : requestedModel,
                finishReason,
                toUsage(metadata == null ? null : metadata.getUsage()),
                requestId
        );
    }

    private ChatUsage toUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        return new ChatUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );
    }

    private ModelClientException mapException(RuntimeException exception) {
        ModelClientException clientException = findCause(exception, ModelClientException.class);
        if (clientException != null) {
            return clientException;
        }
        if (findCause(exception, SocketTimeoutException.class) != null) {
            return new ModelClientException(
                    "Spring AI 模型请求超时",
                    ModelClientErrorType.TIMEOUT,
                    null,
                    exception
            );
        }
        RestClientResponseException responseException = findCause(exception, RestClientResponseException.class);
        if (responseException != null) {
            int status = responseException.getStatusCode().value();
            return new ModelClientException(
                    "Spring AI 模型请求失败: HTTP " + status,
                    ModelClientErrorType.fromHttpStatus(status),
                    status,
                    exception
            );
        }
        if (findCause(exception, ResourceAccessException.class) != null) {
            return new ModelClientException(
                    "Spring AI 模型网络请求失败",
                    ModelClientErrorType.NETWORK_ERROR,
                    null,
                    exception
            );
        }
        if (findCause(exception, RestClientException.class) != null
                || findCause(exception, NonTransientAiException.class) != null) {
            return invalidResponse("Spring AI 模型响应结构非法", exception);
        }
        if (findCause(exception, TransientAiException.class) != null) {
            return new ModelClientException(
                    "Spring AI 模型服务暂时不可用",
                    ModelClientErrorType.PROVIDER_ERROR,
                    null,
                    exception
            );
        }
        return new ModelClientException(
                "Spring AI 模型调用失败",
                ModelClientErrorType.PROVIDER_ERROR,
                null,
                exception
        );
    }

    private ModelTarget resolveTarget(ChatRequest request) {
        AiModelProperties.ModelGroup chat = properties.getChat();
        if (chat == null || chat.getCandidates() == null || chat.getCandidates().isEmpty()) {
            throw new ModelClientException(
                    "Spring AI chat candidates 未配置",
                    ModelClientErrorType.CONFIGURATION_ERROR,
                    null
            );
        }
        AiModelProperties.ModelCandidate candidate = chat.getCandidates().stream()
                .filter(Objects::nonNull)
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .filter(item -> Objects.equals(item.getId(), chat.getDefaultModel()))
                .findFirst()
                .orElseGet(() -> chat.getCandidates().stream()
                        .filter(Objects::nonNull)
                        .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                        .min(Comparator.comparing(
                                AiModelProperties.ModelCandidate::getPriority,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .orElseThrow(() -> new ModelClientException(
                                "Spring AI 没有可用的 chat candidate",
                                ModelClientErrorType.CONFIGURATION_ERROR,
                                null
                        )));

        AiModelProperties.ProviderConfig provider = properties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new ModelClientException(
                    "Spring AI provider 未配置: " + candidate.getProvider(),
                    ModelClientErrorType.CONFIGURATION_ERROR,
                    null
            );
        }
        String model = StringUtils.hasText(request.model())
                ? request.model().trim()
                : requireText(candidate.getModel(), "Spring AI candidate model 未配置");
        return new ModelTarget(model, provider);
    }

    private Duration resolveTimeout(ChatRequest request) {
        Duration configured = properties.getSpringAi().getChat().getTimeout();
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new ModelClientException(
                    "Spring AI timeout 配置无效",
                    ModelClientErrorType.CONFIGURATION_ERROR,
                    null
            );
        }
        return request.timeout() == null ? configured : request.timeout();
    }

    private String resolveEndpoint(AiModelProperties.ProviderConfig provider,
                                   String key,
                                   String defaultPath) {
        Map<String, String> endpoints = provider.getEndpoints();
        if (endpoints == null || !StringUtils.hasText(endpoints.get(key))) {
            return defaultPath;
        }
        return endpoints.get(key).trim();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ModelClientException(message, ModelClientErrorType.CONFIGURATION_ERROR, null);
        }
        return value.trim();
    }

    private ModelClientException invalidResponse(String message, Throwable cause) {
        return new ModelClientException(message, ModelClientErrorType.INVALID_RESPONSE, null, cause);
    }

    private <T extends Throwable> T findCause(Throwable source, Class<T> type) {
        Throwable current = source;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private RetryTemplate noRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
        return retryTemplate;
    }

    private static RestClient.Builder newRestClientBuilder(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static class ModelResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(URI url,
                                HttpMethod method,
                                ClientHttpResponse response) throws IOException {
            HttpStatusCode status = response.getStatusCode();
            throw new ModelClientException(
                    "Spring AI 模型请求失败: HTTP " + status.value(),
                    ModelClientErrorType.fromHttpStatus(status.value()),
                    status.value()
            );
        }
    }

    private record ModelTarget(
            String model,
            AiModelProperties.ProviderConfig provider
    ) {
    }
}
