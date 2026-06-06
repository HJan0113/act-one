package com.hjan.actone.infra.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.ai.model.ChatRole;
import com.hjan.actone.ai.model.ChatUsage;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.error.ModelClientErrorType;
import com.hjan.actone.infra.ai.error.ModelClientException;
import com.hjan.actone.infra.ai.error.ModelClientExceptionMapper;
import com.hjan.actone.infra.ai.config.AiModelProperties;
import com.hjan.actone.infra.ai.model.ModelHealthStore;
import com.hjan.actone.infra.ai.model.ModelRoutingExecutor;
import com.hjan.actone.infra.ai.model.ModelSelector;
import com.hjan.actone.infra.ai.support.FakeChatClient;
import com.hjan.actone.infra.ai.support.LLMResponseCleaner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultLLMServiceTest {

    @Test
    void chatReturnsStandardResponseAndForwardsRequest() {
        FakeChatClient client = new FakeChatClient().responding(new ChatResponse(
                "```json\r\n{\"summary\":\"完成\"}\r\n```",
                "fake-model",
                "stop",
                new ChatUsage(20, 8, 28),
                "request_1"
        ));
        DefaultLLMService service = new DefaultLLMService(client);
        ChatRequest request = new ChatRequest(
                List.of(ChatMessage.system("只返回 JSON"), ChatMessage.user("总结章节")),
                "fake-model",
                0.2,
                0.9,
                512,
                Duration.ofSeconds(10),
                ChatResponseFormat.JSON_OBJECT
        );

        ChatResponse response = service.chat(request);

        assertThat(response.content()).isEqualTo("{\"summary\":\"完成\"}");
        assertThat(response.model()).isEqualTo("fake-model");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.usage().totalTokens()).isEqualTo(28);
        assertThat(response.requestId()).isEqualTo("request_1");
        assertThat(client.requests()).containsExactly(request);
        assertThat(client.requests().get(0).timeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void chatLogsModelMetadataWithoutLeakingPromptOrResponseBody() {
        FakeChatClient client = new FakeChatClient().responding(new ChatResponse(
                "```json\r\n{\"summary\":\"完成\"}\r\n```",
                "fake-model",
                "stop",
                new ChatUsage(20, 8, 28),
                "request_1"
        ));
        DefaultLLMService service = new DefaultLLMService(client);
        ChatRequest request = new ChatRequest(
                List.of(ChatMessage.user("完整小说正文 api-key=secret")),
                "fake-model",
                null,
                null,
                null,
                null,
                ChatResponseFormat.JSON_OBJECT
        );
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        try {
            service.chat(request);
        } finally {
            detachLogAppender(appender);
        }

        String logs = formattedLogs(appender);
        assertThat(logs)
                .contains("event=actone_llm_request_success")
                .contains("provider=fake")
                .contains("model=fake-model")
                .contains("responseModel=fake-model")
                .contains("requestId=request_1")
                .contains("totalTokens=28")
                .doesNotContain("完整小说正文", "api-key", "secret", "{\"summary\"");
    }

    @Test
    void chatFailureLogDoesNotExposeProviderMessage() {
        ModelClientException providerFailure = new ModelClientException(
                "HTTP body contains api-key=secret and source novel",
                ModelClientErrorType.SERVER_ERROR,
                500
        );
        DefaultLLMService service = new DefaultLLMService(new FakeChatClient().failing(providerFailure));
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        try {
            catchThrowableOfType(() -> service.chat("完整小说正文"), RemoteException.class);
        } finally {
            detachLogAppender(appender);
        }

        String logs = formattedLogs(appender);
        assertThat(logs)
                .contains("event=actone_llm_request_failure")
                .contains("provider=fake")
                .contains("model=default")
                .contains("errorType=ModelClientException")
                .contains("errorCode=" + BaseErrorCode.REMOTE_ERROR.code())
                .doesNotContain("HTTP body", "api-key", "secret", "source novel", "完整小说正文");
    }

    @Test
    void chatStringBuildsSingleUserMessage() {
        FakeChatClient client = new FakeChatClient().responding(ChatResponse.text("剧本摘要"));
        DefaultLLMService service = new DefaultLLMService(client);

        ChatResponse response = service.chat("总结这一章");

        assertThat(response.content()).isEqualTo("剧本摘要");
        assertThat(client.requests()).hasSize(1);
        assertThat(client.requests().get(0).messages()).containsExactly(
                new ChatMessage(ChatRole.USER, "总结这一章")
        );
    }

    @Test
    void chatRejectsEmptyResponse() {
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().responding(ChatResponse.text("```json\n  \n```"))
        );

        assertThatThrownBy(() -> service.chat("生成摘要"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("返回内容为空");
    }

    @Test
    void chatFallsBackToNextCandidateAndCleansResponse() {
        AiModelProperties properties = routingProperties();
        ModelHealthStore healthStore = new ModelHealthStore(properties);
        FakeChatClient primary = new FakeChatClient("p1")
                .failing(new ModelClientException("failed", ModelClientErrorType.SERVER_ERROR, 500));
        FakeChatClient secondary = new FakeChatClient("p2")
                .responding(new ChatResponse(
                        "```json\n{\"ok\":true}\n```",
                        "secondary-model",
                        "stop",
                        new ChatUsage(1, 2, 3),
                        "request_2"
                ));
        DefaultLLMService service = new DefaultLLMService(
                new ModelSelector(properties, healthStore),
                new ModelRoutingExecutor(healthStore),
                List.of(primary, secondary),
                new LLMResponseCleaner(),
                new ModelClientExceptionMapper()
        );

        ChatResponse response = service.chat(ChatRequest.of("生成摘要"));

        assertThat(response.content()).isEqualTo("{\"ok\":true}");
        assertThat(response.model()).isEqualTo("secondary-model");
        assertThat(primary.targets()).extracting(target -> target.id()).containsExactly("primary");
        assertThat(secondary.targets()).extracting(target -> target.id()).containsExactly("secondary");
        assertThat(healthStore.isUnavailable("primary")).isFalse();
    }

    @Test
    void chatSanitizesRemoteTimeoutException() {
        RemoteException timeout = new RemoteException(
                "upstream body contains api-key=secret",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().failing(timeout)
        );

        RemoteException actual = catchThrowableOfType(
                () -> service.chat("生成摘要"),
                RemoteException.class
        );

        assertThat(actual)
                .hasMessage("AI 模型调用超时，请稍后重试")
                .hasCause(timeout);
        assertThat(actual.getErrorCode()).isEqualTo(BaseErrorCode.SERVICE_TIMEOUT_ERROR.code());
        assertThat(actual.getErrorMessage()).doesNotContain("api-key", "secret", "upstream body");
    }

    @Test
    void chatMapsUnexpectedClientFailureToRemoteException() {
        IllegalStateException clientFailure = new IllegalStateException("fake client failure");
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().failing(clientFailure)
        );

        RemoteException actual = catchThrowableOfType(
                () -> service.chat("生成摘要"),
                RemoteException.class
        );

        assertThat(actual)
                .hasMessage("AI 模型调用失败，请稍后重试")
                .hasCause(clientFailure);
        assertThat(actual.getErrorMessage()).doesNotContain("fake client failure");
    }

    @ParameterizedTest
    @MethodSource("modelErrorMappings")
    void chatMapsModelFailureWithoutExposingProviderDetails(ModelClientErrorType errorType,
                                                            String expectedMessage,
                                                            BaseErrorCode expectedErrorCode) {
        ModelClientException providerFailure = new ModelClientException(
                "HTTP body contains api-key=secret and source novel",
                errorType,
                500
        );
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().failing(providerFailure)
        );

        RemoteException actual = catchThrowableOfType(
                () -> service.chat("生成摘要"),
                RemoteException.class
        );

        assertThat(actual)
                .hasMessage(expectedMessage)
                .hasCause(providerFailure);
        assertThat(actual.getErrorCode()).isEqualTo(expectedErrorCode.code());
        assertThat(actual.getErrorMessage())
                .doesNotContain("HTTP body", "api-key", "secret", "source novel");
    }

    @Test
    void requestRejectsInvalidStructureAndKeepsDefensiveCopy() {
        assertThatThrownBy(() -> ChatRequest.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("消息列表不能为空");
        assertThatThrownBy(() -> new ChatRequest(
                List.of(ChatMessage.user("内容")),
                null,
                null,
                null,
                null,
                Duration.ZERO,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("原始内容"));
        ChatRequest request = ChatRequest.of(messages);
        messages.add(ChatMessage.assistant("不应进入请求"));

        assertThat(request.messages()).hasSize(1);
        assertThatThrownBy(() -> request.messages().add(ChatMessage.user("不可修改")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Stream<Arguments> modelErrorMappings() {
        return Stream.of(
                Arguments.of(
                        ModelClientErrorType.TIMEOUT,
                        "AI 模型调用超时，请稍后重试",
                        BaseErrorCode.SERVICE_TIMEOUT_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.RATE_LIMITED,
                        "AI 模型请求过于频繁，请稍后重试",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.UNAUTHORIZED,
                        "AI 模型配置不可用，请联系管理员",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.CONFIGURATION_ERROR,
                        "AI 模型配置不可用，请联系管理员",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.INVALID_RESPONSE,
                        "AI 模型返回内容无效，请重试",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.NETWORK_ERROR,
                        "AI 模型服务暂时不可用，请稍后重试",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.SERVER_ERROR,
                        "AI 模型服务暂时不可用，请稍后重试",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.PROVIDER_ERROR,
                        "AI 模型服务暂时不可用，请稍后重试",
                        BaseErrorCode.REMOTE_ERROR
                ),
                Arguments.of(
                        ModelClientErrorType.CLIENT_ERROR,
                        "AI 模型请求失败，请稍后重试",
                        BaseErrorCode.REMOTE_ERROR
                )
        );
    }

    private AiModelProperties routingProperties() {
        AiModelProperties.ProviderConfig p1 = provider();
        AiModelProperties.ProviderConfig p2 = provider();

        AiModelProperties properties = new AiModelProperties();
        properties.setProviders(Map.of("p1", p1, "p2", p2));
        properties.getSelection().setFailureThreshold(2);
        properties.getChat().setDefaultModel("primary");
        properties.getChat().setCandidates(List.of(
                candidate("primary", "p1", "primary-model", 1),
                candidate("secondary", "p2", "secondary-model", 2)
        ));
        return properties;
    }

    private AiModelProperties.ProviderConfig provider() {
        AiModelProperties.ProviderConfig provider = new AiModelProperties.ProviderConfig();
        provider.setUrl("https://mock.example");
        provider.setApiKey("test-key");
        provider.setEndpoints(Map.of("chat", "/v1/chat/completions"));
        return provider;
    }

    private AiModelProperties.ModelCandidate candidate(String id,
                                                       String provider,
                                                       String model,
                                                       int priority) {
        AiModelProperties.ModelCandidate candidate = new AiModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setEnabled(true);
        candidate.setPriority(priority);
        return candidate;
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultLLMService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultLLMService.class);
        logger.detachAppender(appender);
    }

    private String formattedLogs(ListAppender<ILoggingEvent> appender) {
        return String.join(
                "\n",
                appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .toList()
        );
    }
}
