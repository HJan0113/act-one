package com.hjan.actone.script.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.infra.ai.config.AiClientConfiguration;
import com.hjan.actone.infra.ai.prompt.ClasspathPromptTemplateService;
import com.hjan.actone.script.ai.pipeline.CinematicTreatmentStage;
import com.hjan.actone.script.ai.pipeline.ChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.DialogueGenerationStage;
import com.hjan.actone.script.ai.pipeline.DeterministicSourceTraceStage;
import com.hjan.actone.script.ai.pipeline.GlobalStoryExtractionStage;
import com.hjan.actone.script.ai.pipeline.GlobalStoryStateMerger;
import com.hjan.actone.script.ai.pipeline.LlmCinematicTreatmentStage;
import com.hjan.actone.script.ai.pipeline.LlmChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.LlmDialogueGenerationStage;
import com.hjan.actone.script.ai.pipeline.LlmGlobalStoryExtractionStage;
import com.hjan.actone.script.ai.pipeline.LlmScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.LlmScriptYamlGenerationStage;
import com.hjan.actone.script.ai.pipeline.LlmYamlRepairStage;
import com.hjan.actone.script.ai.pipeline.PipelineConsistencyChecker;
import com.hjan.actone.script.ai.pipeline.ScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.SourceTraceStage;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineService;
import com.hjan.actone.script.ai.pipeline.ScriptYamlFactConstraintChecker;
import com.hjan.actone.script.ai.pipeline.ScriptYamlGenerationStage;
import com.hjan.actone.script.ai.pipeline.YamlRepairStage;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScriptAiConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PromptTemplateService.class, () -> new ClasspathPromptTemplateService(new DefaultResourceLoader()));

    @Test
    void registersChapterSummaryStageWhenLlmServiceIsAvailable() {
        contextRunner
                .withPropertyValues("ai.spring-ai.chat.enabled=true")
                .withBean(LLMService.class, StubLlmService::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalStoryStateMerger.class);
                    assertThat(context).hasSingleBean(PipelineConsistencyChecker.class);
                    assertThat(context).hasSingleBean(ScriptYamlFactConstraintChecker.class);
                    assertThat(context).hasSingleBean(ChapterSummaryStage.class);
                    assertThat(context).hasSingleBean(GlobalStoryExtractionStage.class);
                    assertThat(context).hasSingleBean(ScenePlanningStage.class);
                    assertThat(context).hasSingleBean(CinematicTreatmentStage.class);
                    assertThat(context).hasSingleBean(DialogueGenerationStage.class);
                    assertThat(context).hasSingleBean(SourceTraceStage.class);
                    assertThat(context).hasSingleBean(ScriptYamlGenerationStage.class);
                    assertThat(context).hasSingleBean(YamlRepairStage.class);
                    assertThat(context).doesNotHaveBean(ScriptAiPipelineService.class);
                    assertThat(context.getBean(ChapterSummaryStage.class))
                            .isInstanceOf(LlmChapterSummaryStage.class);
                    assertThat(context.getBean(GlobalStoryExtractionStage.class))
                            .isInstanceOf(LlmGlobalStoryExtractionStage.class);
                    assertThat(context.getBean(ScenePlanningStage.class))
                            .isInstanceOf(LlmScenePlanningStage.class);
                    assertThat(context.getBean(CinematicTreatmentStage.class))
                            .isInstanceOf(LlmCinematicTreatmentStage.class);
                    assertThat(context.getBean(DialogueGenerationStage.class))
                            .isInstanceOf(LlmDialogueGenerationStage.class);
                    assertThat(context.getBean(SourceTraceStage.class))
                            .isInstanceOf(DeterministicSourceTraceStage.class);
                    assertThat(context.getBean(ScriptYamlGenerationStage.class))
                            .isInstanceOf(LlmScriptYamlGenerationStage.class);
                    assertThat(context.getBean(YamlRepairStage.class))
                            .isInstanceOf(LlmYamlRepairStage.class);
                });
    }

    @Test
    void registersScriptAiPipelineServiceWhenAllStagesAndBaseServicesAreAvailable() {
        contextRunner
                .withPropertyValues("ai.spring-ai.chat.enabled=true")
                .withBean(LLMService.class, StubLlmService::new)
                .withBean(NovelChapterParser.class, NovelChapterParser::new)
                .withBean(ScriptYamlValidator.class, ScriptYamlValidator::new)
                .withBean(ScriptYamlDraftService.class, () -> new ScriptYamlDraftService(new NovelChapterParser()))
                .run(context -> assertThat(context).hasSingleBean(ScriptAiPipelineService.class));
    }

    @Test
    void registersScriptAiPipelineServiceWhenScriptConfigurationIsProcessedBeforeAiClientConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScriptAiConfiguration.class, AiClientConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(NovelChapterParser.class, NovelChapterParser::new)
                .withBean(ScriptYamlValidator.class, ScriptYamlValidator::new)
                .withBean(ScriptYamlDraftService.class, () -> new ScriptYamlDraftService(new NovelChapterParser()))
                .withPropertyValues(
                        "ai.providers.siliconflow.url=https://mock.example",
                        "ai.providers.siliconflow.api-key=test-api-key",
                        "ai.providers.siliconflow.endpoints.chat=/v1/chat/completions",
                        "ai.spring-ai.chat.enabled=true",
                        "ai.chat.default-model=spring-ai-test",
                        "ai.chat.candidates[0].id=spring-ai-test",
                        "ai.chat.candidates[0].provider=siliconflow",
                        "ai.chat.candidates[0].model=test-model",
                        "ai.chat.candidates[0].enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(ScriptAiPipelineService.class));
    }

    @Test
    void bindsScriptAiCostAndTimeoutProperties() {
        contextRunner
                .withPropertyValues(
                        "script.ai.max-stage-retries=2",
                        "script.ai.timeouts.chapter-summary=7s",
                        "script.ai.timeouts.script-yaml-generation=30s"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ScriptAiProperties.class);
                    ScriptAiProperties properties = context.getBean(ScriptAiProperties.class);
                    assertThat(properties.getMaxStageRetries()).isEqualTo(2);
                    assertThat(properties.getTimeouts().getChapterSummary()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.getTimeouts().getScriptYamlGeneration()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void doesNotRegisterChapterSummaryStageWithoutLlmService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GlobalStoryStateMerger.class);
            assertThat(context).hasSingleBean(PipelineConsistencyChecker.class);
            assertThat(context).hasSingleBean(ScriptYamlFactConstraintChecker.class);
            assertThat(context).hasSingleBean(SourceTraceStage.class);
            assertThat(context.getBean(SourceTraceStage.class))
                    .isInstanceOf(DeterministicSourceTraceStage.class);
            assertThat(context).doesNotHaveBean(ChapterSummaryStage.class);
            assertThat(context).doesNotHaveBean(GlobalStoryExtractionStage.class);
            assertThat(context).doesNotHaveBean(ScenePlanningStage.class);
            assertThat(context).doesNotHaveBean(CinematicTreatmentStage.class);
            assertThat(context).doesNotHaveBean(DialogueGenerationStage.class);
            assertThat(context).doesNotHaveBean(ScriptYamlGenerationStage.class);
            assertThat(context).doesNotHaveBean(YamlRepairStage.class);
            assertThat(context).doesNotHaveBean(ScriptAiPipelineService.class);
        });
    }

    private static final class StubLlmService implements LLMService {

        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.text("{}");
        }
    }
}
