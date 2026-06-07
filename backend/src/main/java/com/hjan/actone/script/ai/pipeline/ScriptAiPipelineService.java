package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.common.log.AiExceptionDiagnostics;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ScriptAiPipelineService {

    private static final String RULE_BASED_SCAFFOLD_MODE = "generation_mode: \"rule_based_scaffold\"";
    private static final String RULE_BASED_FALLBACK_MODE = "generation_mode: \"rule_based_fallback\"";
    private static final String UNKNOWN_MODEL = "n/a";
    private static final int DEFAULT_RETRY = 0;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final NovelChapterParser chapterParser;
    private final ScriptYamlValidator yamlValidator;
    private final ScriptYamlDraftService ruleBasedDraftService;
    private final ChapterSummaryStage chapterSummaryStage;
    private final ScenePlanningStage scenePlanningStage;
    private final ScriptYamlGenerationStage scriptYamlGenerationStage;
    private final ScriptAiPipelineOptions options;
    private final Executor chapterSummaryExecutor;

    public ScriptAiPipelineService(NovelChapterParser chapterParser,
                                   ScriptYamlValidator yamlValidator,
                                   ScriptYamlDraftService ruleBasedDraftService,
                                   ChapterSummaryStage chapterSummaryStage,
                                   ScenePlanningStage scenePlanningStage,
                                   ScriptYamlGenerationStage scriptYamlGenerationStage) {
        this(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                chapterSummaryStage,
                scenePlanningStage,
                scriptYamlGenerationStage,
                ScriptAiPipelineOptions.defaults()
        );
    }

    public ScriptAiPipelineService(NovelChapterParser chapterParser,
                                   ScriptYamlValidator yamlValidator,
                                   ScriptYamlDraftService ruleBasedDraftService,
                                   ChapterSummaryStage chapterSummaryStage,
                                   ScenePlanningStage scenePlanningStage,
                                   ScriptYamlGenerationStage scriptYamlGenerationStage,
                                   ScriptAiPipelineOptions options) {
        this(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                chapterSummaryStage,
                scenePlanningStage,
                scriptYamlGenerationStage,
                options,
                DIRECT_EXECUTOR
        );
    }

    public ScriptAiPipelineService(NovelChapterParser chapterParser,
                                   ScriptYamlValidator yamlValidator,
                                   ScriptYamlDraftService ruleBasedDraftService,
                                   ChapterSummaryStage chapterSummaryStage,
                                   ScenePlanningStage scenePlanningStage,
                                   ScriptYamlGenerationStage scriptYamlGenerationStage,
                                   ScriptAiPipelineOptions options,
                                   Executor chapterSummaryExecutor) {
        this.chapterParser = Objects.requireNonNull(chapterParser, "NovelChapterParser 不能为空");
        this.yamlValidator = Objects.requireNonNull(yamlValidator, "ScriptYamlValidator 不能为空");
        this.ruleBasedDraftService = Objects.requireNonNull(ruleBasedDraftService, "ScriptYamlDraftService 不能为空");
        this.chapterSummaryStage = Objects.requireNonNull(chapterSummaryStage, "ChapterSummaryStage 不能为空");
        this.scenePlanningStage = Objects.requireNonNull(scenePlanningStage, "ScenePlanningStage 不能为空");
        this.scriptYamlGenerationStage = Objects.requireNonNull(
                scriptYamlGenerationStage,
                "ScriptYamlGenerationStage 不能为空"
        );
        this.options = options == null ? ScriptAiPipelineOptions.defaults() : options;
        this.chapterSummaryExecutor = Objects.requireNonNull(chapterSummaryExecutor, "chapterSummaryExecutor 不能为空");
    }

    public ScriptAiPipelineResult generate(ScriptYamlGenerateRequest request) {
        return generate(request, ScriptAiPipelineObserver.NOOP);
    }

    public ScriptAiPipelineResult generate(ScriptYamlGenerateRequest request, ScriptAiPipelineObserver observer) {
        return generate(request, observer, ScriptAiPipelineResumeState.empty());
    }

    public ScriptAiPipelineResult generate(ScriptYamlGenerateRequest request,
                                           ScriptAiPipelineObserver observer,
                                           ScriptAiPipelineResumeState resumeState) {
        long pipelineStartedNanos = System.nanoTime();
        Objects.requireNonNull(request, "ScriptYamlGenerateRequest 不能为空");
        ScriptAiPipelineObserver actualObserver = observer == null ? ScriptAiPipelineObserver.NOOP : observer;
        ScriptAiPipelineResumeState actualResumeState = resumeState == null
                ? ScriptAiPipelineResumeState.empty()
                : resumeState;
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
        actualObserver.onPipelineStart(context);
        context = applyResumeState(context, actualResumeState, actualObserver);

        try (ScriptAiUsageTracker ignored = ScriptAiUsageTracker.start()) {
            try {
                if (context.chapterSummaries().isEmpty()) {
                    context = summarizeChapters(context, chapters, actualObserver);
                }
                if (context.scenePlan() == null) {
                    context = planScenes(context, actualObserver);
                }
                context = generateYaml(context, actualObserver);
                ScriptAiPipelineResult result = validateOrFallback(context, request, actualObserver);
                logPipelineComplete(result, pipelineStartedNanos);
                actualObserver.onComplete(result);
                return result;
            } catch (ScriptAiPipelineCanceledException ex) {
                throw ex;
            } catch (ScriptAiPipelineStageException ex) {
                ScriptAiPipelineResult result = fallback(
                        request,
                        context,
                        ex.stage(),
                        "AI 阶段 " + ex.stage() + " 执行失败，已显式降级为规则版脚手架",
                        actualObserver
                );
                logPipelineComplete(result, pipelineStartedNanos);
                actualObserver.onComplete(result);
                return result;
            } catch (RuntimeException ex) {
                ScriptAiPipelineResult result = fallback(
                        request,
                        context,
                        context.currentStage(),
                        "AI 阶段 " + context.currentStage() + " 执行失败，已显式降级为规则版脚手架",
                        actualObserver
                );
                logPipelineComplete(result, pipelineStartedNanos);
                actualObserver.onComplete(result);
                return result;
            }
        }
    }

    private PipelineContext applyResumeState(PipelineContext context,
                                             ScriptAiPipelineResumeState resumeState,
                                             ScriptAiPipelineObserver observer) {
        PipelineContext nextContext = context;
        if (resumeState.hasChapterSummaries()) {
            nextContext = nextContext.withChapterSummaries(resumeState.chapterSummaries());
            notifyRestoredStage(context, PipelineStage.CHAPTER_SUMMARY, nextContext, observer);
        }
        if (resumeState.hasScenePlan()) {
            nextContext = nextContext.withScenePlan(resumeState.scenePlan());
            notifyRestoredStage(context, PipelineStage.SCENE_PLANNING, nextContext, observer);
        }
        return nextContext;
    }

    private void notifyRestoredStage(PipelineContext originalContext,
                                     PipelineStage stage,
                                     PipelineContext restoredContext,
                                     ScriptAiPipelineObserver observer) {
        observer.onStageResult(originalContext, stage, restoredContext);
        observer.onStageSuccess(restoredContext, stage);
        log.info("阶段已恢复 taskId={} stage={} model={} retry={} chapterCount={}",
                restoredContext.taskId(), stage, UNKNOWN_MODEL, DEFAULT_RETRY, restoredContext.chapterCount());
    }

    private PipelineContext summarizeChapters(PipelineContext context,
                                              List<NovelChapter> chapters,
                                              ScriptAiPipelineObserver observer) {
        Map<String, ChapterSummary> summaryCache = new ConcurrentHashMap<>();
        return runAiStage(context, PipelineStage.CHAPTER_SUMMARY, observer, () -> {
            List<ChapterSummary> summaries = summarizeChaptersConcurrently(chapters, summaryCache);
            return context.withChapterSummaries(summaries);
        });
    }

    private List<ChapterSummary> summarizeChaptersConcurrently(List<NovelChapter> chapters,
                                                               Map<String, ChapterSummary> summaryCache) {
        ScriptAiUsageTracker usageTracker = ScriptAiUsageTracker.currentTracker();
        ScriptAiLlmCallTrace.Trace trace = ScriptAiLlmCallTrace.current();
        List<CompletableFuture<ChapterSummary>> tasks = chapters.stream()
                .map(chapter -> CompletableFuture.supplyAsync(
                        () -> summarizeChapterWithContext(chapter, summaryCache, usageTracker, trace),
                        chapterSummaryExecutor
                ))
                .toList();
        joinAll(tasks);
        return tasks.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private ChapterSummary summarizeChapterWithContext(NovelChapter chapter,
                                                       Map<String, ChapterSummary> summaryCache,
                                                       ScriptAiUsageTracker usageTracker,
                                                       ScriptAiLlmCallTrace.Trace trace) {
        try (ScriptAiUsageTracker.Scope ignoredUsage = ScriptAiUsageTracker.bind(usageTracker);
             ScriptAiLlmCallTrace.Scope ignoredTrace = ScriptAiLlmCallTrace.bind(trace)) {
            return summaryCache.computeIfAbsent(chapter.id(), ignored -> chapterSummaryStage.summarize(chapter));
        }
    }

    private void joinAll(List<? extends CompletableFuture<?>> tasks) {
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ex) {
            throw unwrapCompletionException(ex);
        }
    }

    private RuntimeException unwrapCompletionException(CompletionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return ex;
    }

    private PipelineContext planScenes(PipelineContext context, ScriptAiPipelineObserver observer) {
        return runAiStage(
                context,
                PipelineStage.SCENE_PLANNING,
                observer,
                () -> context.withScenePlan(scenePlanningStage.plan(context))
        );
    }

    private PipelineContext generateYaml(PipelineContext context, ScriptAiPipelineObserver observer) {
        return runAiStage(context, PipelineStage.SCRIPT_YAML_GENERATION, observer, () -> {
            String yaml = scriptYamlGenerationStage.generate(context);
            if (!hasText(yaml)) {
                throw new IllegalStateException("AI YAML 生成结果为空");
            }
            return context.withGeneratedYaml(yaml);
        });
    }

    private ScriptAiPipelineResult validateOrFallback(PipelineContext context,
                                                      ScriptYamlGenerateRequest request,
                                                      ScriptAiPipelineObserver observer) {
        ScriptYamlValidationResult validationResult = validateYaml(
                context,
                context.generatedYaml(),
                DEFAULT_RETRY,
                observer
        );
        context = context.withValidation(validationResult.status(), validationResult.errorMessage());
        if (validationResult.valid()) {
            return success(context.completed(), validationResult.cleanedYaml(), validationResult);
        }
        return fallback(
                request,
                context,
                PipelineStage.YAML_VALIDATION,
                "AI YAML Schema 校验失败，已显式降级为规则版脚手架",
                observer
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
                context,
                ScriptAiUsageTracker.currentSummary()
        );
    }

    private ScriptAiPipelineResult fallback(ScriptYamlGenerateRequest request,
                                            PipelineContext context,
                                            PipelineStage fallbackStage,
                                            String reason,
                                            ScriptAiPipelineObserver observer) {
        long startedNanos = System.nanoTime();
        observer.onFallback(context, fallbackStage, reason);
        log.warn("AI管线降级 taskId={} stage={} model={} retry={} 原因={}",
                context.taskId(), fallbackStage, UNKNOWN_MODEL, DEFAULT_RETRY, reason);
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
                fallbackContext,
                ScriptAiUsageTracker.currentSummary()
        );
        log.info("降级成功 taskId={} stage={} model={} retry={} 耗时={}ms 校验状态={} 是否有效={}",
                context.taskId(), fallbackStage, UNKNOWN_MODEL, DEFAULT_RETRY,
                elapsedMillis(startedNanos), validationResult.status(), validationResult.valid());
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

    private ScriptYamlValidationResult validateYaml(PipelineContext context,
                                                    String yaml,
                                                    int retry,
                                                    ScriptAiPipelineObserver observer) {
        ScriptYamlValidationResult validationResult = runStage(
                context,
                PipelineStage.YAML_VALIDATION,
                retry,
                observer,
                () -> yamlValidator.validate(yaml)
        );
        observer.onValidation(context, validationResult);
        log.info("YAML校验结果 taskId={} stage={} model={} retry={} 校验状态={} 是否有效={} 是否有错误={} 错误={}",
                context.taskId(), PipelineStage.YAML_VALIDATION, UNKNOWN_MODEL, retry,
                validationResult.status(), validationResult.valid(), hasText(validationResult.errorMessage()),
                validationResult.errorMessage());
        return validationResult;
    }

    private <T> T runStage(PipelineContext context,
                           PipelineStage stage,
                           int retry,
                           ScriptAiPipelineObserver observer,
                           StageAction<T> action) {
        return runStage(context, stage, retry, 0, observer, action);
    }

    private <T> T runAiStage(PipelineContext context,
                             PipelineStage stage,
                             ScriptAiPipelineObserver observer,
                             StageAction<T> action) {
        return runStage(context, stage, DEFAULT_RETRY, options.maxStageRetries(), observer, action);
    }

    private <T> T runStage(PipelineContext context,
                           PipelineStage stage,
                           int retry,
                           int maxRetries,
                           ScriptAiPipelineObserver observer,
                           StageAction<T> action) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (observer.isCanceled()) {
                throw new ScriptAiPipelineCanceledException(stage);
            }
            int currentRetry = retry + attempt;
            long startedNanos = System.nanoTime();
            observer.onStageStart(context, stage);
            log.info("阶段开始 taskId={} stage={} model={} retry={} chapterCount={}",
                    context.taskId(), stage, UNKNOWN_MODEL, currentRetry, context.chapterCount());
            try {
                T result;
                try (ScriptAiLlmCallTrace.Scope ignored = ScriptAiLlmCallTrace.open(
                        context.taskId(),
                        stage,
                        currentRetry
                )) {
                    result = action.execute();
                }
                observer.onStageResult(context, stage, result);
                if (observer.isCanceled()) {
                    throw new ScriptAiPipelineCanceledException(stage);
                }
                observer.onStageSuccess(context, stage);
                log.info("阶段成功 taskId={} stage={} model={} retry={} 耗时={}ms chapterCount={}",
                        context.taskId(), stage, UNKNOWN_MODEL, currentRetry,
                        elapsedMillis(startedNanos), context.chapterCount());
                return result;
            } catch (ScriptAiPipelineCanceledException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                lastError = ex;
                long durationMs = elapsedMillis(startedNanos);
                if (attempt < maxRetries) {
                    log.warn("阶段重试 taskId={} stage={} model={} retry={} 下次重试={} 最大重试={} 耗时={}ms 错误类型={} 错误码={} 错误信息={} 模型错误类型={} 模型状态={} 根因类型={} 根因信息={}",
                            context.taskId(), stage, UNKNOWN_MODEL, currentRetry,
                            currentRetry + 1, maxRetries, durationMs,
                            AiExceptionDiagnostics.errorType(ex), AiExceptionDiagnostics.errorCode(ex),
                            AiExceptionDiagnostics.safeMessage(ex), AiExceptionDiagnostics.modelErrorType(ex),
                            AiExceptionDiagnostics.modelStatusCode(ex), AiExceptionDiagnostics.rootCauseType(ex),
                            AiExceptionDiagnostics.rootCauseSafeMessage(ex));
                    continue;
                }
                observer.onStageFailure(context, stage, ex);
                log.warn("阶段失败 taskId={} stage={} model={} retry={} 耗时={}ms 错误类型={} 错误码={} 错误信息={} 模型错误类型={} 模型状态={} 根因类型={} 根因信息={}",
                        context.taskId(), stage, UNKNOWN_MODEL, currentRetry, durationMs,
                        AiExceptionDiagnostics.errorType(ex), AiExceptionDiagnostics.errorCode(ex),
                        AiExceptionDiagnostics.safeMessage(ex), AiExceptionDiagnostics.modelErrorType(ex),
                        AiExceptionDiagnostics.modelStatusCode(ex), AiExceptionDiagnostics.rootCauseType(ex),
                        AiExceptionDiagnostics.rootCauseSafeMessage(ex));
                throw new ScriptAiPipelineStageException(stage, ex);
            }
        }
        throw new ScriptAiPipelineStageException(stage, Objects.requireNonNull(lastError));
    }

    private void logPipelineStart(PipelineContext context, ScriptYamlGenerateRequest request) {
        log.info("管线启动 taskId={} stage={} model={} retry={} 章节数={} 是否有标题={} 是否有作者={} 是否有梗概={}",
                context.taskId(), PipelineStage.INIT, UNKNOWN_MODEL, DEFAULT_RETRY,
                context.chapterCount(), hasText(request.title()), hasText(request.author()),
                hasText(request.logline()));
    }

    private void logPipelineComplete(ScriptAiPipelineResult result, long startedNanos) {
        log.info("管线完成 taskId={} stage={} model={} retry={} 耗时={}ms 生成模式={} 是否降级={} 校验状态={} 是否有效={} 章节数={} LLM调用={} 提示token={} 生成token={} 总token={} token是否估算={}",
                result.context().taskId(), result.context().currentStage(), UNKNOWN_MODEL, DEFAULT_RETRY,
                elapsedMillis(startedNanos), result.generationMode().yamlValue(), result.fallback(),
                result.validationResult() == null ? null : result.validationResult().status(),
                result.valid(), result.chapterCount(),
                result.usageSummary().callCount(), result.usageSummary().promptTokens(),
                result.usageSummary().completionTokens(), result.usageSummary().totalTokens(),
                result.usageSummary().estimated());
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    @FunctionalInterface
    private interface StageAction<T> {

        T execute();
    }
}
