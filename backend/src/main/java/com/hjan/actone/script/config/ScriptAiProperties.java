package com.hjan.actone.script.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "script.ai")
public class ScriptAiProperties {

    private int maxStageRetries = 1;
    private Timeouts timeouts = new Timeouts();
    private Concurrency concurrency = new Concurrency();

    @Data
    public static class Timeouts {

        private Duration chapterSummary = Duration.ofSeconds(60);
        private Duration scenePlanning = Duration.ofSeconds(90);
        private Duration scriptYamlGeneration = Duration.ofSeconds(120);
    }

    @Data
    public static class Concurrency {

        private int chapterSummaryCorePoolSize = 4;
        private int chapterSummaryMaxPoolSize = 4;
        private int chapterSummaryQueueCapacity = 64;
        private int scenePlanningCorePoolSize = 3;
        private int scenePlanningMaxPoolSize = 3;
        private int scenePlanningQueueCapacity = 32;
        private int yamlGenerationCorePoolSize = 3;
        private int yamlGenerationMaxPoolSize = 3;
        private int yamlGenerationQueueCapacity = 32;
    }
}
