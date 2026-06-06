package com.hjan.actone.script.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.script.ai.pipeline.ChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.GlobalStoryExtractionStage;
import com.hjan.actone.script.ai.pipeline.GlobalStoryStateMerger;
import com.hjan.actone.script.ai.pipeline.LlmChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.LlmGlobalStoryExtractionStage;
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
}
