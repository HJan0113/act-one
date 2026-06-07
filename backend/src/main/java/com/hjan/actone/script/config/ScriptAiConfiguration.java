package com.hjan.actone.script.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.script.ai.pipeline.ChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.LlmChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.LlmScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.LlmScriptYamlGenerationStage;
import com.hjan.actone.script.ai.pipeline.ScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.ScriptAiMeteredLlmService;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineOptions;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineService;
import com.hjan.actone.script.ai.pipeline.ScriptYamlGenerationStage;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(ScriptAiProperties.class)
public class ScriptAiConfiguration {

    private static final String CHAPTER_SUMMARY_EXECUTOR = "scriptAiChapterSummaryExecutor";

    @Bean(name = CHAPTER_SUMMARY_EXECUTOR)
    @ConditionalOnMissingBean(name = CHAPTER_SUMMARY_EXECUTOR)
    public ThreadPoolTaskExecutor scriptAiChapterSummaryExecutor(ScriptAiProperties scriptAiProperties) {
        ScriptAiProperties.Concurrency concurrency = scriptAiProperties.getConcurrency();
        int corePoolSize = positive(concurrency.getChapterSummaryCorePoolSize(), 2);
        int maxPoolSize = Math.max(corePoolSize, positive(concurrency.getChapterSummaryMaxPoolSize(), 4));
        int queueCapacity = Math.max(0, concurrency.getChapterSummaryQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("script-ai-summary-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ChapterSummaryStage.class)
    public ChapterSummaryStage chapterSummaryStage(LLMService llmService,
                                                   PromptTemplateService promptTemplateService,
                                                   ObjectMapper objectMapper,
                                                   ScriptAiProperties scriptAiProperties) {
        return new LlmChapterSummaryStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getChapterSummary()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ScenePlanningStage.class)
    public ScenePlanningStage scenePlanningStage(LLMService llmService,
                                                 PromptTemplateService promptTemplateService,
                                                 ObjectMapper objectMapper,
                                                 ScriptAiProperties scriptAiProperties) {
        return new LlmScenePlanningStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getScenePlanning()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ScriptYamlGenerationStage.class)
    public ScriptYamlGenerationStage scriptYamlGenerationStage(LLMService llmService,
                                                               PromptTemplateService promptTemplateService,
                                                               ObjectMapper objectMapper,
                                                               ScriptAiProperties scriptAiProperties) {
        return new LlmScriptYamlGenerationStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getScriptYamlGeneration()
        );
    }

    @Bean
    @ConditionalOnBean({
            NovelChapterParser.class,
            ScriptYamlValidator.class,
            ScriptYamlDraftService.class,
            ChapterSummaryStage.class,
            ScenePlanningStage.class,
            ScriptYamlGenerationStage.class
    })
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ScriptAiPipelineService.class)
    public ScriptAiPipelineService scriptAiPipelineService(NovelChapterParser chapterParser,
                                                           ScriptYamlValidator yamlValidator,
                                                           ScriptYamlDraftService ruleBasedDraftService,
                                                           ChapterSummaryStage chapterSummaryStage,
                                                           ScenePlanningStage scenePlanningStage,
                                                           ScriptYamlGenerationStage scriptYamlGenerationStage,
                                                           @Qualifier(CHAPTER_SUMMARY_EXECUTOR) Executor chapterSummaryExecutor,
                                                           ScriptAiProperties scriptAiProperties) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                chapterSummaryStage,
                scenePlanningStage,
                scriptYamlGenerationStage,
                new ScriptAiPipelineOptions(scriptAiProperties.getMaxStageRetries()),
                chapterSummaryExecutor
        );
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
