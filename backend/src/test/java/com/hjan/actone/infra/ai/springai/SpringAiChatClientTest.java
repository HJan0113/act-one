package com.hjan.actone.infra.ai.springai;

import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.infra.ai.config.AiModelProperties;
import com.hjan.actone.infra.ai.error.ModelClientErrorType;
import com.hjan.actone.infra.ai.error.ModelClientException;
import com.hjan.actone.infra.ai.model.ModelTarget;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class SpringAiChatClientTest {

    @Test
    void chatUsesSpringAiCompatibleRequestAndParsesResponse() {
        Fixture fixture = fixture("request-model");
        fixture.server().expect(requestTo("https://mock.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.model").value("request-model"))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.top_p").value(0.8))
                .andExpect(jsonPath("$.max_tokens").value(512))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(successResponse("{\"summary\":\"完成\"}"), MediaType.APPLICATION_JSON));
        ChatRequest request = new ChatRequest(
                List.of(ChatMessage.system("只返回 JSON"), ChatMessage.user("总结章节")),
                "request-model",
                0.2,
                0.8,
                512,
                Duration.ofSeconds(1),
                ChatResponseFormat.JSON_OBJECT
        );

        var response = fixture.client().chat(request, fixture.target());

        assertThat(response.content()).isEqualTo("{\"summary\":\"完成\"}");
        assertThat(response.model()).isEqualTo("response-model");
        assertThat(response.finishReason()).isEqualTo("STOP");
        assertThat(response.requestId()).isEqualTo("chatcmpl_1");
        assertThat(response.usage().promptTokens()).isEqualTo(12);
        assertThat(response.usage().completionTokens()).isEqualTo(8);
        assertThat(response.usage().totalTokens()).isEqualTo(20);
        fixture.server().verify();
    }

    @Test
    void chatUsesConfiguredDefaults() {
        Fixture fixture = fixture("configured-model");
        fixture.server().expect(requestTo("https://mock.example/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("configured-model"))
                .andExpect(jsonPath("$.temperature").value(0.3))
                .andExpect(jsonPath("$.top_p").value(0.9))
                .andExpect(jsonPath("$.max_tokens").value(4096))
                .andRespond(withSuccess(successResponse("默认响应"), MediaType.APPLICATION_JSON));

        var response = fixture.client().chat(ChatRequest.of("生成摘要"), fixture.target());

        assertThat(response.content()).isEqualTo("默认响应");
        fixture.server().verify();
    }

    @Test
    void chatRejectsEmptyContent() {
        Fixture fixture = fixture("configured-model");
        fixture.server().expect(requestTo("https://mock.example/v1/chat/completions"))
                .andRespond(withSuccess(successResponse("  "), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().chat(ChatRequest.of("生成摘要"), fixture.target()))
                .isInstanceOf(ModelClientException.class)
                .hasMessageContaining("返回内容为空")
                .extracting("errorType")
                .isEqualTo(ModelClientErrorType.INVALID_RESPONSE);
        fixture.server().verify();
    }

    @Test
    void chatClassifiesHttpError() {
        Fixture fixture = fixture("configured-model");
        fixture.server().expect(requestTo("https://mock.example/v1/chat/completions"))
                .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> fixture.client().chat(ChatRequest.of("生成摘要"), fixture.target()))
                .isInstanceOf(ModelClientException.class)
                .hasMessage("Spring AI 模型请求失败: HTTP 429")
                .extracting("errorType", "statusCode")
                .containsExactly(ModelClientErrorType.RATE_LIMITED, 429);
        fixture.server().verify();
    }

    @Test
    void chatClassifiesTimeout() {
        Fixture fixture = fixture("configured-model");
        fixture.server().expect(requestTo("https://mock.example/v1/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> fixture.client().chat(ChatRequest.of("生成摘要"), fixture.target()))
                .isInstanceOf(ModelClientException.class)
                .hasMessageContaining("请求超时")
                .extracting("errorType")
                .isEqualTo(ModelClientErrorType.TIMEOUT);
        fixture.server().verify();
    }

    @Test
    void chatRejectsInvalidJson() {
        Fixture fixture = fixture("configured-model");
        fixture.server().expect(requestTo("https://mock.example/v1/chat/completions"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().chat(ChatRequest.of("生成摘要"), fixture.target()))
                .isInstanceOf(ModelClientException.class)
                .hasMessageContaining("响应结构非法")
                .extracting("errorType")
                .isEqualTo(ModelClientErrorType.INVALID_RESPONSE);
        fixture.server().verify();
    }

    private Fixture fixture(String model) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiModelProperties properties = properties();
        SpringAiChatClient client = new SpringAiChatClient(
                SpringAiProviderIds.OPENAI_COMPATIBLE,
                properties.getSpringAi().getChat(),
                timeout -> builder
        );
        AiModelProperties.ModelCandidate candidate = properties.getChat().getCandidates().get(0);
        ModelTarget target = new ModelTarget(
                candidate.getId(),
                model,
                candidate,
                properties.getProviders().get(candidate.getProvider())
        );
        return new Fixture(client, server, target);
    }

    private AiModelProperties properties() {
        AiModelProperties.ProviderConfig provider = new AiModelProperties.ProviderConfig();
        provider.setUrl("https://mock.example");
        provider.setApiKey("test-api-key");
        provider.setEndpoints(Map.of(
                "chat", "/v1/chat/completions",
                "embedding", "/v1/embeddings"
        ));

        AiModelProperties.ModelCandidate candidate = new AiModelProperties.ModelCandidate();
        candidate.setId("spring-ai-test");
        candidate.setProvider("siliconflow");
        candidate.setModel("configured-model");
        candidate.setEnabled(true);
        candidate.setPriority(1);

        AiModelProperties properties = new AiModelProperties();
        properties.setProviders(Map.of("siliconflow", provider));
        properties.getSpringAi().getChat().setEnabled(true);
        properties.getSpringAi().getChat().setTemperature(0.3);
        properties.getSpringAi().getChat().setTopP(0.9);
        properties.getSpringAi().getChat().setMaxTokens(4096);
        properties.getSpringAi().getChat().setTimeout(Duration.ofSeconds(2));
        properties.getChat().setDefaultModel("spring-ai-test");
        properties.getChat().setCandidates(List.of(candidate));
        return properties;
    }

    private String successResponse(String content) {
        return """
                {
                  "id": "chatcmpl_1",
                  "object": "chat.completion",
                  "created": 1710000000,
                  "model": "response-model",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 8,
                    "total_tokens": 20
                  }
                }
                """.formatted(content.replace("\\", "\\\\").replace("\"", "\\\""));
    }

    private record Fixture(
            SpringAiChatClient client,
            MockRestServiceServer server,
            ModelTarget target
    ) {
    }
}
