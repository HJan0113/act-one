package com.hjan.actone.infra.ai.model;

import com.hjan.actone.infra.ai.config.AiModelProperties;

public record ModelTarget(
        String id,
        String model,
        AiModelProperties.ModelCandidate candidate,
        AiModelProperties.ProviderConfig provider
) {
}
