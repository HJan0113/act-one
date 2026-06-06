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
import com.hjan.actone.script.ai.pipeline.LlmScriptYamlGenerationStage;
import com.hjan.actone.script.ai.pipeline.LlmYamlRepairStage;
import com.hjan.actone.script.ai.pipeline.PipelineConsistencyChecker;
import com.hjan.actone.script.ai.pipeline.ScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.SourceTraceStage;
import com.hjan.actone.script.ai.pipeline.ScriptAiMeteredLlmService;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineOptions;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineService;
import com.hjan.actone.script.ai.pipeline.ScriptYamlGenerationStage;
import com.hjan.actone.script.ai.pipeline.ScriptYamlFactConstraintChecker;
import com.hjan.actone.script.ai.pipeline.YamlRepairStage;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScriptAiProperties.class)
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
    @ConditionalOnMissingBean(PipelineConsistencyChecker.class)
    public PipelineConsistencyChecker pipelineConsistencyChecker() {
        return new PipelineConsistencyChecker();
    }

    @Bean
    @ConditionalOnMissingBean(ScriptYamlFactConstraintChecker.class)
    public ScriptYamlFactConstraintChecker scriptYamlFactConstraintChecker() {
        return new ScriptYamlFactConstraintChecker();
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
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
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(GlobalStoryExtractionStage.class)
    public GlobalStoryExtractionStage globalStoryExtractionStage(LLMService llmService,
                                                                 PromptTemplateService promptTemplateService,
                                                                 ObjectMapper objectMapper,
                                                                 GlobalStoryStateMerger globalStoryStateMerger,
                                                                 ScriptAiProperties scriptAiProperties) {
        return new LlmGlobalStoryExtractionStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                globalStoryStateMerger,
                scriptAiProperties.getTimeouts().getGlobalStoryExtraction()
        );
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
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
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(CinematicTreatmentStage.class)
    public CinematicTreatmentStage cinematicTreatmentStage(LLMService llmService,
                                                           PromptTemplateService promptTemplateService,
                                                           ObjectMapper objectMapper,
                                                           ScriptAiProperties scriptAiProperties) {
        return new LlmCinematicTreatmentStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getCinematicTreatment()
        );
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
    @ConditionalOnMissingBean(DialogueGenerationStage.class)
    public DialogueGenerationStage dialogueGenerationStage(LLMService llmService,
                                                           PromptTemplateService promptTemplateService,
                                                           ObjectMapper objectMapper,
                                                           ScriptAiProperties scriptAiProperties) {
        return new LlmDialogueGenerationStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                objectMapper,
                scriptAiProperties.getTimeouts().getDialogueGeneration()
        );
    }

    @Bean
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class, ObjectMapper.class})
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
    @ConditionalOnBean({LLMService.class, PromptTemplateService.class})
    @ConditionalOnMissingBean(YamlRepairStage.class)
    public YamlRepairStage yamlRepairStage(LLMService llmService,
                                           PromptTemplateService promptTemplateService,
                                           ScriptAiProperties scriptAiProperties) {
        return new LlmYamlRepairStage(
                ScriptAiMeteredLlmService.wrap(llmService),
                promptTemplateService,
                scriptAiProperties.getTimeouts().getYamlRepair()
        );
    }

    @Bean
    @ConditionalOnBean({
            NovelChapterParser.class,
            ScriptYamlValidator.class,
            ScriptYamlDraftService.class,
            ChapterSummaryStage.class,
            GlobalStoryExtractionStage.class,
            ScenePlanningStage.class,
            CinematicTreatmentStage.class,
            DialogueGenerationStage.class,
            SourceTraceStage.class,
            ScriptYamlGenerationStage.class,
            YamlRepairStage.class,
            GlobalStoryStateMerger.class,
            PipelineConsistencyChecker.class,
            ScriptYamlFactConstraintChecker.class
    })
    @ConditionalOnMissingBean(ScriptAiPipelineService.class)
    public ScriptAiPipelineService scriptAiPipelineService(NovelChapterParser chapterParser,
                                                           ScriptYamlValidator yamlValidator,
                                                           ScriptYamlDraftService ruleBasedDraftService,
                                                           ChapterSummaryStage chapterSummaryStage,
                                                           GlobalStoryExtractionStage globalStoryExtractionStage,
                                                           ScenePlanningStage scenePlanningStage,
                                                           CinematicTreatmentStage cinematicTreatmentStage,
                                                           DialogueGenerationStage dialogueGenerationStage,
                                                           SourceTraceStage sourceTraceStage,
                                                           ScriptYamlGenerationStage scriptYamlGenerationStage,
                                                           YamlRepairStage yamlRepairStage,
                                                           GlobalStoryStateMerger globalStoryStateMerger,
                                                           PipelineConsistencyChecker consistencyChecker,
                                                           ScriptYamlFactConstraintChecker factConstraintChecker,
                                                           ScriptAiProperties scriptAiProperties) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                chapterSummaryStage,
                globalStoryExtractionStage,
                scenePlanningStage,
                cinematicTreatmentStage,
                dialogueGenerationStage,
                sourceTraceStage,
                scriptYamlGenerationStage,
                yamlRepairStage,
                globalStoryStateMerger,
                consistencyChecker,
                factConstraintChecker,
                new ScriptAiPipelineOptions(scriptAiProperties.getMaxStageRetries())
        );
    }
}
