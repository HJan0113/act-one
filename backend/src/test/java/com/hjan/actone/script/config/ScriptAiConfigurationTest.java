package com.hjan.actone.script.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.service.LLMService;
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
import com.hjan.actone.script.ai.pipeline.ScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.SourceTraceStage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScriptAiConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PromptTemplateService.class, () -> new ClasspathPromptTemplateService(new DefaultResourceLoader()));

    @Test
    void registersChapterSummaryStageWhenLlmServiceIsAvailable() {
        contextRunner
                .withBean(LLMService.class, StubLlmService::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalStoryStateMerger.class);
                    assertThat(context).hasSingleBean(ChapterSummaryStage.class);
                    assertThat(context).hasSingleBean(GlobalStoryExtractionStage.class);
                    assertThat(context).hasSingleBean(ScenePlanningStage.class);
                    assertThat(context).hasSingleBean(CinematicTreatmentStage.class);
                    assertThat(context).hasSingleBean(DialogueGenerationStage.class);
                    assertThat(context).hasSingleBean(SourceTraceStage.class);
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
                });
    }

    @Test
    void doesNotRegisterChapterSummaryStageWithoutLlmService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GlobalStoryStateMerger.class);
            assertThat(context).hasSingleBean(SourceTraceStage.class);
            assertThat(context.getBean(SourceTraceStage.class))
                    .isInstanceOf(DeterministicSourceTraceStage.class);
            assertThat(context).doesNotHaveBean(ChapterSummaryStage.class);
            assertThat(context).doesNotHaveBean(GlobalStoryExtractionStage.class);
            assertThat(context).doesNotHaveBean(ScenePlanningStage.class);
            assertThat(context).doesNotHaveBean(CinematicTreatmentStage.class);
            assertThat(context).doesNotHaveBean(DialogueGenerationStage.class);
        });
    }

    private static final class StubLlmService implements LLMService {

        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.text("{}");
        }
    }
}
