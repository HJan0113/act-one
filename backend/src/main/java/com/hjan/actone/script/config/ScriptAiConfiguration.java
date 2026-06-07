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
    private static final String SCENE_PLANNING_EXECUTOR = "scriptAiScenePlanningExecutor";
    private static final String YAML_GENERATION_EXECUTOR = "scriptAiYamlGenerationExecutor";

    @Bean(name = CHAPTER_SUMMARY_EXECUTOR)
    @ConditionalOnMissingBean(name = CHAPTER_SUMMARY_EXECUTOR)
    public ThreadPoolTaskExecutor scriptAiChapterSummaryExecutor(ScriptAiProperties scriptAiProperties) {
        ScriptAiProperties.Concurrency concurrency = scriptAiProperties.getConcurrency();
        return buildExecutor(
                "script-ai-summary-",
                positive(concurrency.getChapterSummaryCorePoolSize(), 4),
                positive(concurrency.getChapterSummaryMaxPoolSize(), 4),
                concurrency.getChapterSummaryQueueCapacity()
        );
    }

    @Bean(name = SCENE_PLANNING_EXECUTOR)
    @ConditionalOnMissingBean(name = SCENE_PLANNING_EXECUTOR)
    public ThreadPoolTaskExecutor scriptAiScenePlanningExecutor(ScriptAiProperties scriptAiProperties) {
        ScriptAiProperties.Concurrency concurrency = scriptAiProperties.getConcurrency();
        return buildExecutor(
                "script-ai-plan-",
                positive(concurrency.getScenePlanningCorePoolSize(), 3),
                positive(concurrency.getScenePlanningMaxPoolSize(), 3),
                concurrency.getScenePlanningQueueCapacity()
        );
    }

    @Bean(name = YAML_GENERATION_EXECUTOR)
    @ConditionalOnMissingBean(name = YAML_GENERATION_EXECUTOR)
    public ThreadPoolTaskExecutor scriptAiYamlGenerationExecutor(ScriptAiProperties scriptAiProperties) {
        ScriptAiProperties.Concurrency concurrency = scriptAiProperties.getConcurrency();
        return buildExecutor(
                "script-ai-yaml-",
                positive(concurrency.getYamlGenerationCorePoolSize(), 3),
                positive(concurrency.getYamlGenerationMaxPoolSize(), 3),
                concurrency.getYamlGenerationQueueCapacity()
        );
    }

    private ThreadPoolTaskExecutor buildExecutor(String threadNamePrefix,
                                                 int corePoolSize,
                                                 int configuredMaxPoolSize,
                                                 int configuredQueueCapacity) {
        int maxPoolSize = Math.max(corePoolSize, configuredMaxPoolSize);
        int queueCapacity = Math.max(0, configuredQueueCapacity);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
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
                                                 @Qualifier(SCENE_PLANNING_EXECUTOR) Executor scenePlanningExecutor,
                                                 ScriptAiProperties scriptAiProperties) {
        return new LlmScenePlanningStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getScenePlanning(),
                scenePlanningExecutor
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ScriptYamlGenerationStage.class)
    public ScriptYamlGenerationStage scriptYamlGenerationStage(LLMService llmService,
                                                               PromptTemplateService promptTemplateService,
                                                               ObjectMapper objectMapper,
                                                               @Qualifier(YAML_GENERATION_EXECUTOR) Executor yamlGenerationExecutor,
                                                               ScriptAiProperties scriptAiProperties) {
        return new LlmScriptYamlGenerationStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getScriptYamlGeneration(),
                yamlGenerationExecutor
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
