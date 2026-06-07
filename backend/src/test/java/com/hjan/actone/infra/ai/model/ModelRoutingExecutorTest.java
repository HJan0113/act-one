package com.hjan.actone.infra.ai.model;

import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.config.AiModelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRoutingExecutorTest {

    @Test
    void executeWithFallbackUsesNextTargetAfterFailure() {
        AiModelProperties properties = new AiModelProperties();
        properties.getSelection().setFailureThreshold(1);
        ModelHealthStore healthStore = new ModelHealthStore(properties);
        ModelRoutingExecutor executor = new ModelRoutingExecutor(healthStore);
        ModelTarget first = target("m1", "p1");
        ModelTarget second = target("m2", "p2");

        String result = executor.executeWithFallback(
                "chat",
                List.of(first, second),
                target -> target.id(),
                (client, target) -> {
                    if ("m1".equals(target.id())) {
                        throw new IllegalStateException("failed");
                    }
                    return "ok-" + client;
                }
        );

        assertThat(result).isEqualTo("ok-m2");
        assertThat(healthStore.isUnavailable("m1")).isTrue();
        assertThat(healthStore.isUnavailable("m2")).isFalse();
    }

    @Test
    void executeWithFallbackRejectsEmptyTargets() {
        ModelRoutingExecutor executor = new ModelRoutingExecutor(new ModelHealthStore(new AiModelProperties()));

        assertThatThrownBy(() -> executor.executeWithFallback(
                "chat",
                List.of(),
                target -> target.id(),
                (client, target) -> "ok"
        ))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("没有可用 chat 模型候选");
    }

    private ModelTarget target(String id, String providerId) {
        AiModelProperties.ProviderConfig provider = new AiModelProperties.ProviderConfig();
        provider.setUrl("https://mock.example");
        provider.setApiKey("test-key");
        provider.setEndpoints(Map.of("chat", "/v1/chat/completions"));

        AiModelProperties.ModelCandidate candidate = new AiModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(providerId);
        candidate.setModel(id + "-model");
        candidate.setEnabled(true);
        candidate.setPriority(1);
        return new ModelTarget(id, candidate.getModel(), candidate, provider);
    }
}
