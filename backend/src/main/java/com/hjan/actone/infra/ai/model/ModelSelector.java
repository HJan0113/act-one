package com.hjan.actone.infra.ai.model;

import com.hjan.actone.infra.ai.config.AiModelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModelSelector {

    private static final Logger log = LoggerFactory.getLogger(ModelSelector.class);

    private final AiModelProperties properties;
    private final ModelHealthStore healthStore;

    public ModelSelector(AiModelProperties properties, ModelHealthStore healthStore) {
        this.properties = Objects.requireNonNull(properties, "AiModelProperties 不能为空");
        this.healthStore = Objects.requireNonNull(healthStore, "ModelHealthStore 不能为空");
    }

    public List<ModelTarget> selectChatCandidates(String requestedModel) {
        AiModelProperties.ModelGroup group = properties.getChat();
        if (group == null || group.getCandidates() == null || group.getCandidates().isEmpty()) {
            return List.of();
        }
        String requested = normalize(requestedModel);
        boolean requestMatchesCandidate = requested != null && group.getCandidates().stream()
                .filter(Objects::nonNull)
                .anyMatch(candidate -> matchesRequestedModel(candidate, requested));
        String firstChoice = requestMatchesCandidate ? requested : normalize(group.getDefaultModel());
        Map<String, AiModelProperties.ProviderConfig> providers = properties.getProviders();

        return group.getCandidates().stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !Boolean.FALSE.equals(candidate.getEnabled()))
                .sorted(candidateComparator(firstChoice))
                .map(candidate -> buildTarget(candidate, providers, requested, requestMatchesCandidate))
                .filter(Objects::nonNull)
                .toList();
    }

    private Comparator<AiModelProperties.ModelCandidate> candidateComparator(String firstChoice) {
        return Comparator
                .comparing((AiModelProperties.ModelCandidate candidate) -> !matchesFirstChoice(candidate, firstChoice))
                .thenComparing(AiModelProperties.ModelCandidate::getPriority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(this::resolveId, Comparator.nullsLast(String::compareTo));
    }

    private ModelTarget buildTarget(AiModelProperties.ModelCandidate candidate,
                                    Map<String, AiModelProperties.ProviderConfig> providers,
                                    String requestedModel,
                                    boolean requestMatchesCandidate) {
        String id = resolveId(candidate);
        if (healthStore.isUnavailable(id)) {
            return null;
        }
        if (!StringUtils.hasText(candidate.getProvider())) {
            log.warn("event=actone_model_candidate_invalid reason=provider_missing modelId={}", id);
            return null;
        }
        AiModelProperties.ProviderConfig provider = providers == null ? null : providers.get(candidate.getProvider());
        if (provider == null) {
            log.warn("event=actone_model_candidate_invalid reason=provider_config_missing provider={} modelId={}",
                    candidate.getProvider(), id);
            return null;
        }
        String configuredModel = normalize(candidate.getModel());
        String model = resolveEffectiveModel(candidate, requestedModel, requestMatchesCandidate, configuredModel);
        if (model == null) {
            log.warn("event=actone_model_candidate_invalid reason=model_missing provider={} modelId={}",
                    candidate.getProvider(), id);
            return null;
        }
        return new ModelTarget(id, model, candidate, provider);
    }

    private String resolveEffectiveModel(AiModelProperties.ModelCandidate candidate,
                                         String requestedModel,
                                         boolean requestMatchesCandidate,
                                         String configuredModel) {
        if (requestedModel != null && !requestMatchesCandidate) {
            return requestedModel;
        }
        if (requestedModel != null && Objects.equals(requestedModel, normalize(candidate.getModel()))) {
            return requestedModel;
        }
        return configuredModel;
    }

    private boolean matchesFirstChoice(AiModelProperties.ModelCandidate candidate, String firstChoice) {
        return firstChoice != null && matchesRequestedModel(candidate, firstChoice);
    }

    private boolean matchesRequestedModel(AiModelProperties.ModelCandidate candidate, String requestedModel) {
        return Objects.equals(resolveId(candidate), requestedModel)
                || Objects.equals(normalize(candidate.getModel()), requestedModel);
    }

    private String resolveId(AiModelProperties.ModelCandidate candidate) {
        String id = normalize(candidate.getId());
        if (id != null) {
            return id;
        }
        String provider = normalize(candidate.getProvider());
        String model = normalize(candidate.getModel());
        return (provider == null ? "unknown" : provider) + "::" + (model == null ? "unknown" : model);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
