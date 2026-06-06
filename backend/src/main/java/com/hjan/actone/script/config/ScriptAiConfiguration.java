package com.hjan.actone.script.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.service.LLMService;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScriptAiConfiguration {

    @Bean
    @ConditionalOnMissingBean(GlobalStoryStateMerger.class)
    public GlobalStoryStateMerger globalStoryStateMerger() {
        return new GlobalStoryStateMerger();
    }

    @Bean
    @ConditionalOnMissingBean(SourceTraceStage.class)
    public SourceTraceStage sourceTraceStage() {
        return new DeterministicSourceTraceStage();
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(ChapterSummaryStage.class)
    public ChapterSummaryStage chapterSummaryStage(LLMService llmService,
                                                   PromptTemplateService promptTemplateService,
                                                   ObjectMapper objectMapper) {
        return new LlmChapterSummaryStage(llmService, promptTemplateService, objectMapper);
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(GlobalStoryExtractionStage.class)
    public GlobalStoryExtractionStage globalStoryExtractionStage(LLMService llmService,
                                                                 PromptTemplateService promptTemplateService,
                                                                 ObjectMapper objectMapper,
                                                                 GlobalStoryStateMerger globalStoryStateMerger) {
        return new LlmGlobalStoryExtractionStage(llmService, promptTemplateService, objectMapper, globalStoryStateMerger);
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(ScenePlanningStage.class)
    public ScenePlanningStage scenePlanningStage(LLMService llmService,
                                                 PromptTemplateService promptTemplateService,
                                                 ObjectMapper objectMapper) {
        return new LlmScenePlanningStage(llmService, promptTemplateService, objectMapper);
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(CinematicTreatmentStage.class)
    public CinematicTreatmentStage cinematicTreatmentStage(LLMService llmService,
                                                           PromptTemplateService promptTemplateService,
                                                           ObjectMapper objectMapper) {
        return new LlmCinematicTreatmentStage(llmService, promptTemplateService, objectMapper);
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(DialogueGenerationStage.class)
    public DialogueGenerationStage dialogueGenerationStage(LLMService llmService,
                                                           PromptTemplateService promptTemplateService,
                                                           ObjectMapper objectMapper) {
        return new LlmDialogueGenerationStage(llmService, promptTemplateService, objectMapper);
    }
}
