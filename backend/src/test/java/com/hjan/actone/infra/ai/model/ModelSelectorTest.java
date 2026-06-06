package com.hjan.actone.infra.ai.model;

import com.hjan.actone.infra.ai.config.AiModelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSelectorTest {

    @Test
    void selectChatCandidatesPrefersDefaultAndFiltersInvalidCandidates() {
        AiModelProperties properties = properties(
                "default",
                candidate("fast", "p1", "fast-model", true, 1),
                candidate("default", "p1", "default-model", true, 100),
                candidate("disabled", "p1", "disabled-model", false, 0),
                candidate("missing-provider", "missing", "missing-model", true, 0)
        );
        ModelSelector selector = selector(properties);

        List<ModelTarget> targets = selector.selectChatCandidates(null);

        assertThat(targets).extracting(ModelTarget::id)
                .containsExactly("default", "fast");
        assertThat(targets).extracting(ModelTarget::model)
                .containsExactly("default-model", "fast-model");
    }

    @Test
    void selectChatCandidatesPrefersRequestedCandidate() {
        AiModelProperties properties = properties(
                "default",
                candidate("default", "p1", "default-model", true, 1),
                candidate("custom", "p2", "custom-model", true, 10)
        );
        ModelSelector selector = selector(properties);

        List<ModelTarget> targets = selector.selectChatCandidates("custom");

        assertThat(targets).extracting(ModelTarget::id)
                .containsExactly("custom", "default");
        assertThat(targets.get(0).model()).isEqualTo("custom-model");
    }

    @Test
    void selectChatCandidatesKeepsUnmatchedRequestModelAsOverride() {
        AiModelProperties properties = properties(
                "default",
                candidate("default", "p1", "default-model", true, 1)
        );
        ModelSelector selector = selector(properties);

        List<ModelTarget> targets = selector.selectChatCandidates("request-model");

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).id()).isEqualTo("default");
        assertThat(targets.get(0).model()).isEqualTo("request-model");
    }

    @Test
    void selectChatCandidatesSkipsOpenModel() {
        AiModelProperties properties = properties(
                "default",
                candidate("default", "p1", "default-model", true, 1)
        );
        properties.getSelection().setFailureThreshold(1);
        ModelHealthStore healthStore = new ModelHealthStore(properties);
        healthStore.markFailure("default");
        ModelSelector selector = new ModelSelector(properties, healthStore);

        assertThat(selector.selectChatCandidates(null)).isEmpty();
    }

    private ModelSelector selector(AiModelProperties properties) {
        return new ModelSelector(properties, new ModelHealthStore(properties));
    }

    private AiModelProperties properties(String defaultModel,
                                         AiModelProperties.ModelCandidate... candidates) {
        AiModelProperties.ProviderConfig p1 = provider();
        AiModelProperties.ProviderConfig p2 = provider();
        AiModelProperties properties = new AiModelProperties();
        properties.setProviders(Map.of("p1", p1, "p2", p2));
        properties.getChat().setDefaultModel(defaultModel);
        properties.getChat().setCandidates(List.of(candidates));
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
                                                       Boolean enabled,
                                                       Integer priority) {
        AiModelProperties.ModelCandidate candidate = new AiModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setEnabled(enabled);
        candidate.setPriority(priority);
        return candidate;
    }
}
