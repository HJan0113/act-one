package com.hjan.actone.script.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "script.ai")
public class ScriptAiProperties {

    private int maxStageRetries = 1;
    private Timeouts timeouts = new Timeouts();

    @Data
    public static class Timeouts {

        private Duration chapterSummary = Duration.ofSeconds(60);
        private Duration globalStoryExtraction = Duration.ofSeconds(90);
        private Duration scenePlanning = Duration.ofSeconds(90);
        private Duration cinematicTreatment = Duration.ofSeconds(90);
        private Duration dialogueGeneration = Duration.ofSeconds(90);
        private Duration scriptYamlGeneration = Duration.ofSeconds(120);
        private Duration yamlRepair = Duration.ofSeconds(120);
    }
}
