package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ScriptAiPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ScriptAiPipelineService.class);
    private static final String RULE_BASED_SCAFFOLD_MODE = "generation_mode: \"rule_based_scaffold\"";
    private static final String RULE_BASED_FALLBACK_MODE = "generation_mode: \"rule_based_fallback\"";
    private static final String UNKNOWN_MODEL = "n/a";
    private static final int DEFAULT_RETRY = 0;

    private final NovelChapterParser chapterParser;
    private final ScriptYamlValidator yamlValidator;
    private final ScriptYamlDraftService ruleBasedDraftService;
    private final ChapterSummaryStage chapterSummaryStage;
    private final GlobalStoryExtractionStage globalStoryExtractionStage;
    private final ScenePlanningStage scenePlanningStage;
    private final CinematicTreatmentStage cinematicTreatmentStage;
    private final DialogueGenerationStage dialogueGenerationStage;
    private final ScriptYamlGenerationStage scriptYamlGenerationStage;
    private final YamlRepairStage yamlRepairStage;

    public ScriptAiPipelineService(NovelChapterParser chapterParser,
                                   ScriptYamlValidator yamlValidator,
                                   ScriptYamlDraftService ruleBasedDraftService,
                                   ChapterSummaryStage chapterSummaryStage,
                                   GlobalStoryExtractionStage globalStoryExtractionStage,
                                   ScenePlanningStage scenePlanningStage,
                                   CinematicTreatmentStage cinematicTreatmentStage,
                                   DialogueGenerationStage dialogueGenerationStage,
                                   ScriptYamlGenerationStage scriptYamlGenerationStage,
                                   YamlRepairStage yamlRepairStage) {
        this.chapterParser = Objects.requireNonNull(chapterParser, "NovelChapterParser 不能为空");
        this.yamlValidator = Objects.requireNonNull(yamlValidator, "ScriptYamlValidator 不能为空");
        this.ruleBasedDraftService = Objects.requireNonNull(ruleBasedDraftService, "ScriptYamlDraftService 不能为空");
        this.chapterSummaryStage = Objects.requireNonNull(chapterSummaryStage, "ChapterSummaryStage 不能为空");
        this.globalStoryExtractionStage = Objects.requireNonNull(
                globalStoryExtractionStage,
                "GlobalStoryExtractionStage 不能为空"
        );
        this.scenePlanningStage = Objects.requireNonNull(scenePlanningStage, "ScenePlanningStage 不能为空");
        this.cinematicTreatmentStage = Objects.requireNonNull(
                cinematicTreatmentStage,
                "CinematicTreatmentStage 不能为空"
        );
        this.dialogueGenerationStage = Objects.requireNonNull(
                dialogueGenerationStage,
                "DialogueGenerationStage 不能为空"
        );
        this.scriptYamlGenerationStage = Objects.requireNonNull(
                scriptYamlGenerationStage,
                "ScriptYamlGenerationStage 不能为空"
        );
        this.yamlRepairStage = Objects.requireNonNull(yamlRepairStage, "YamlRepairStage 不能为空");
    }

    public ScriptAiPipelineResult generate(ScriptYamlGenerateRequest request) {
        long pipelineStartedNanos = System.nanoTime();
        Objects.requireNonNull(request, "ScriptYamlGenerateRequest 不能为空");
        List<NovelChapter> chapters = chapterParser.parse(request.text());
        if (chapters.size() < NovelChapterParser.MIN_CHAPTER_COUNT) {
            throw new ClientException("至少需要 3 个章节，当前识别到 " + chapters.size() + " 个。");
        }

        String title = resolveTitle(request.title(), chapters);
        PipelineContext context = PipelineContext.create(
                null,
                title,
                request.author(),
                request.logline(),
                chapters.size()
        );
        logPipelineStart(context, request);

        try {
            context = summarizeChapters(context, chapters);
            context = extractGlobalStoryState(context);
            context = planScenes(context);
            context = visualizePsychology(context);
            context = generateDialogues(context);
            context = generateYaml(context);
            ScriptAiPipelineResult result = validateAndRepairIfNeeded(context, request);
            logPipelineComplete(result, pipelineStartedNanos);
            return result;
        } catch (ScriptAiPipelineStageException ex) {
            ScriptAiPipelineResult result = fallback(
                    request,
                    context,
                    ex.stage(),
                    "AI 阶段 " + ex.stage() + " 执行失败，已显式降级为规则版脚手架"
            );
            logPipelineComplete(result, pipelineStartedNanos);
            return result;
        } catch (RuntimeException ex) {
            ScriptAiPipelineResult result = fallback(
                    request,
                    context,
                    context.currentStage(),
                    "AI 阶段 " + context.currentStage() + " 执行失败，已显式降级为规则版脚手架"
            );
            logPipelineComplete(result, pipelineStartedNanos);
            return result;
        }
    }

    private PipelineContext summarizeChapters(PipelineContext context, List<NovelChapter> chapters) {
        return runStage(context, PipelineStage.CHAPTER_SUMMARY, DEFAULT_RETRY, () -> {
            List<ChapterSummary> summaries = new ArrayList<>(chapters.size());
            for (NovelChapter chapter : chapters) {
                summaries.add(chapterSummaryStage.summarize(chapter));
            }
            return context.withChapterSummaries(summaries);
        });
    }

    private PipelineContext extractGlobalStoryState(PipelineContext context) {
        return runStage(
                context,
                PipelineStage.GLOBAL_STORY_EXTRACTION,
                DEFAULT_RETRY,
                () -> context.withGlobalStoryState(globalStoryExtractionStage.extract(context))
        );
    }

    private PipelineContext planScenes(PipelineContext context) {
        return runStage(
                context,
                PipelineStage.SCENE_PLANNING,
                DEFAULT_RETRY,
                () -> context.withScenePlan(scenePlanningStage.plan(context))
        );
    }

    private PipelineContext visualizePsychology(PipelineContext context) {
        return runStage(
                context,
                PipelineStage.PSYCHOLOGICAL_VISUALIZATION,
                DEFAULT_RETRY,
                () -> context.withCinematicTreatment(cinematicTreatmentStage.visualize(context))
        );
    }

    private PipelineContext generateDialogues(PipelineContext context) {
        return runStage(
                context,
                PipelineStage.DIALOGUE_GENERATION,
                DEFAULT_RETRY,
                () -> context.withDialogueDraft(dialogueGenerationStage.draft(context))
        );
    }

    private PipelineContext generateYaml(PipelineContext context) {
        return runStage(context, PipelineStage.SCRIPT_YAML_GENERATION, DEFAULT_RETRY, () -> {
            String yaml = scriptYamlGenerationStage.generate(context);
            if (!hasText(yaml)) {
                throw new IllegalStateException("AI YAML 生成结果为空");
            }
            return context.withGeneratedYaml(yaml);
        });
    }

    private ScriptAiPipelineResult validateAndRepairIfNeeded(PipelineContext context,
                                                             ScriptYamlGenerateRequest request) {
        ScriptYamlValidationResult validationResult = validateYaml(context, context.generatedYaml(), DEFAULT_RETRY);
        context = context.withValidation(validationResult.status(), validationResult.errorMessage());
        if (validationResult.valid()) {
            return success(context.completed(), validationResult.cleanedYaml(), validationResult);
        }

        PipelineContext repairContext = context;
        String repairedYaml = runStage(
                repairContext,
                PipelineStage.YAML_REPAIR,
                DEFAULT_RETRY,
                () -> yamlRepairStage.repair(repairContext, validationResult)
        );
        if (!hasText(repairedYaml)) {
            return fallback(
                    request,
                    context,
                    PipelineStage.YAML_REPAIR,
                    "AI YAML 校验失败且修复结果为空，已显式降级为规则版脚手架"
            );
        }
        context = context.withRepairedYaml(repairedYaml);

        ScriptYamlValidationResult repairedValidation = validateYaml(context, repairedYaml, 1);
        context = context.withValidation(repairedValidation.status(), repairedValidation.errorMessage());
        if (repairedValidation.valid()) {
            return success(context.completed(), repairedValidation.cleanedYaml(), repairedValidation);
        }
        return fallback(
                request,
                context,
                PipelineStage.YAML_VALIDATION,
                "AI YAML 校验失败且自动修复未通过，已显式降级为规则版脚手架"
        );
    }

    private ScriptAiPipelineResult success(PipelineContext context,
                                           String yaml,
                                           ScriptYamlValidationResult validationResult) {
        return new ScriptAiPipelineResult(
                ScriptYamlDraftService.SCHEMA_VERSION,
                context.projectTitle(),
                context.chapterCount(),
                yaml,
                ScriptAiGenerationMode.AI_PIPELINE,
                false,
                null,
                validationResult,
                context
        );
    }

    private ScriptAiPipelineResult fallback(ScriptYamlGenerateRequest request,
                                            PipelineContext context,
                                            PipelineStage fallbackStage,
                                            String reason) {
        long startedNanos = System.nanoTime();
        log.warn(
                "event=actone_ai_pipeline_fallback taskId={} stage={} model={} retry={} reason={}",
                context.taskId(),
                fallbackStage,
                UNKNOWN_MODEL,
                DEFAULT_RETRY,
                reason
        );
        ScriptYamlGenerateResponse scaffold = ruleBasedDraftService.generateFromText(request);
        String fallbackYaml = markFallback(scaffold.yaml());
        ScriptYamlValidationResult validationResult = yamlValidator.validate(fallbackYaml);
        PipelineContext fallbackContext = context.withGeneratedYaml(fallbackYaml)
                .withValidation(validationResult.status(), validationResult.errorMessage())
                .completed();
        ScriptAiPipelineResult result = new ScriptAiPipelineResult(
                scaffold.schemaVersion(),
                scaffold.title(),
                scaffold.chapterCount(),
                validationResult.cleanedYaml(),
                ScriptAiGenerationMode.RULE_BASED_FALLBACK,
                true,
                reason,
                validationResult,
                fallbackContext
        );
        log.info(
                "event=actone_ai_pipeline_fallback_success taskId={} stage={} model={} retry={} durationMs={} validationStatus={} valid={}",
                context.taskId(),
                fallbackStage,
                UNKNOWN_MODEL,
                DEFAULT_RETRY,
                elapsedMillis(startedNanos),
                validationResult.status(),
                validationResult.valid()
        );
        return result;
    }

    private String markFallback(String yaml) {
        if (yaml.contains(RULE_BASED_SCAFFOLD_MODE)) {
            return yaml.replace(RULE_BASED_SCAFFOLD_MODE, RULE_BASED_FALLBACK_MODE);
        }
        return yaml;
    }

    private String resolveTitle(String title, List<NovelChapter> chapters) {
        if (hasText(title)) {
            return title.trim();
        }
        if (!chapters.isEmpty() && hasText(chapters.get(0).title())) {
            return chapters.get(0).title();
        }
        return "未命名剧本";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ScriptYamlValidationResult validateYaml(PipelineContext context, String yaml, int retry) {
        ScriptYamlValidationResult validationResult = runStage(
                context,
                PipelineStage.YAML_VALIDATION,
                retry,
                () -> yamlValidator.validate(yaml)
        );
        log.info(
                "event=actone_ai_yaml_validation_result taskId={} stage={} model={} retry={} validationStatus={} valid={} errorPresent={}",
                context.taskId(),
                PipelineStage.YAML_VALIDATION,
                UNKNOWN_MODEL,
                retry,
                validationResult.status(),
                validationResult.valid(),
                hasText(validationResult.errorMessage())
        );
        return validationResult;
    }

    private <T> T runStage(PipelineContext context, PipelineStage stage, int retry, StageAction<T> action) {
        long startedNanos = System.nanoTime();
        log.info(
                "event=actone_ai_stage_start taskId={} stage={} model={} retry={} chapterCount={}",
                context.taskId(),
                stage,
                UNKNOWN_MODEL,
                retry,
                context.chapterCount()
        );
        try {
            T result = action.execute();
            log.info(
                    "event=actone_ai_stage_success taskId={} stage={} model={} retry={} durationMs={} chapterCount={}",
                    context.taskId(),
                    stage,
                    UNKNOWN_MODEL,
                    retry,
                    elapsedMillis(startedNanos),
                    context.chapterCount()
            );
            return result;
        } catch (RuntimeException ex) {
            log.warn(
                    "event=actone_ai_stage_failure taskId={} stage={} model={} retry={} durationMs={} errorType={}",
                    context.taskId(),
                    stage,
                    UNKNOWN_MODEL,
                    retry,
                    elapsedMillis(startedNanos),
                    ex.getClass().getSimpleName()
            );
            throw new ScriptAiPipelineStageException(stage, ex);
        }
    }

    private void logPipelineStart(PipelineContext context, ScriptYamlGenerateRequest request) {
        log.info(
                "event=actone_ai_pipeline_start taskId={} stage={} model={} retry={} chapterCount={} titlePresent={} authorPresent={} loglinePresent={}",
                context.taskId(),
                PipelineStage.INIT,
                UNKNOWN_MODEL,
                DEFAULT_RETRY,
                context.chapterCount(),
                hasText(request.title()),
                hasText(request.author()),
                hasText(request.logline())
        );
    }

    private void logPipelineComplete(ScriptAiPipelineResult result, long startedNanos) {
        log.info(
                "event=actone_ai_pipeline_complete taskId={} stage={} model={} retry={} durationMs={} generationMode={} fallback={} validationStatus={} valid={} chapterCount={}",
                result.context().taskId(),
                result.context().currentStage(),
                UNKNOWN_MODEL,
                DEFAULT_RETRY,
                elapsedMillis(startedNanos),
                result.generationMode().yamlValue(),
                result.fallback(),
                result.validationResult() == null ? null : result.validationResult().status(),
                result.valid(),
                result.chapterCount()
        );
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    @FunctionalInterface
    private interface StageAction<T> {

        T execute();
    }
}
