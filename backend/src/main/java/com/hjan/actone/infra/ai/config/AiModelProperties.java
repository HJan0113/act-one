package com.hjan.actone.infra.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {

    private Map<String, ProviderConfig> providers = new HashMap<>();
    private SpringAiConfig springAi = new SpringAiConfig();
    private ModelGroup chat = new ModelGroup();

    @Data
    public static class ProviderConfig {
        private String url;
        private String apiKey;
        private Map<String, String> endpoints = new HashMap<>();
    }

    @Data
    public static class SpringAiConfig {
        private ChatSettings chat = new ChatSettings();
    }

    @Data
    public static class ChatSettings {
        private boolean enabled;
        private Double temperature = 0.3;
        private Double topP = 0.9;
        private Integer maxTokens = 4096;
        private Duration timeout = Duration.ofSeconds(60);
    }

    @Data
    public static class ModelGroup {
        private String defaultModel;
        private List<ModelCandidate> candidates = new ArrayList<>();
    }

    @Data
    public static class ModelCandidate {
        private String id;
        private String provider;
        private String model;
        private Boolean enabled = true;
        private Integer priority = 100;
    }
}
