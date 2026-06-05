package com.hjan.actone.infra.ai;

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
import com.hjan.actone.infra.ai.support.FakeChatClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultLLMServiceTest {

    @Test
    void chatReturnsStandardResponseAndForwardsRequest() {
        FakeChatClient client = new FakeChatClient().responding(new ChatResponse(
                "{\"summary\":\"完成\"}",
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
        assertThat(response.usage().totalTokens()).isEqualTo(28);
        assertThat(client.requests()).containsExactly(request);
        assertThat(client.requests().get(0).timeout()).isEqualTo(Duration.ofSeconds(10));
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
                new FakeChatClient().responding(ChatResponse.text("  "))
        );

        assertThatThrownBy(() -> service.chat("生成摘要"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("返回内容为空");
    }

    @Test
    void chatPreservesRemoteTimeoutException() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().failing(timeout)
        );

        assertThatThrownBy(() -> service.chat("生成摘要"))
                .isSameAs(timeout)
                .extracting("errorCode")
                .isEqualTo(BaseErrorCode.SERVICE_TIMEOUT_ERROR.code());
    }

    @Test
    void chatMapsUnexpectedClientFailureToRemoteException() {
        IllegalStateException clientFailure = new IllegalStateException("fake client failure");
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().failing(clientFailure)
        );

        assertThatThrownBy(() -> service.chat("生成摘要"))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 模型调用失败，请稍后重试")
                .hasCause(clientFailure);
    }

    @Test
    void chatMapsModelTimeoutToServiceTimeoutError() {
        ModelClientException timeout = new ModelClientException(
                "provider timeout",
                ModelClientErrorType.TIMEOUT,
                null
        );
        DefaultLLMService service = new DefaultLLMService(
                new FakeChatClient().failing(timeout)
        );

        assertThatThrownBy(() -> service.chat("生成摘要"))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 模型调用超时，请稍后重试")
                .hasCause(timeout)
                .extracting("errorCode")
                .isEqualTo(BaseErrorCode.SERVICE_TIMEOUT_ERROR.code());
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
}
