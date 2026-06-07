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

        private Duration chapterSummary = Duration.ofSeconds(300);
        private Duration scenePlanning = Duration.ofSeconds(480);
        private Duration scriptYamlGeneration = Duration.ofSeconds(480);
    }

    @Data
    public static class Concurrency {

        private int chapterSummaryCorePoolSize = 0;
        private int chapterSummaryMaxPoolSize = 0;
        private int chapterSummaryQueueCapacity = 64;
        private int scenePlanningCorePoolSize = 0;
        private int scenePlanningMaxPoolSize = 0;
        private int scenePlanningQueueCapacity = 32;
        private int yamlGenerationCorePoolSize = 0;
        private int yamlGenerationMaxPoolSize = 0;
        private int yamlGenerationQueueCapacity = 32;
    }
}
