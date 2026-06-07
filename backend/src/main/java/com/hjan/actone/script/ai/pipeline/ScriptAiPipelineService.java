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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScriptAiPipelineService {

    private static final String RULE_BASED_SCAFFOLD_MODE = "generation_mode: \"rule_based_scaffold\"";
    private static final String RULE_BASED_FALLBACK_MODE = "generation_mode: \"rule_based_fallback\"";

    private final NovelChapterParser chapterParser;
    private final ScriptYamlValidator yamlValidator;
    private final ScriptYamlDraftService ruleBasedDraftService;
    private final ChapterSummaryStage chapterSummaryStage;
    private final GlobalStoryExtractionStage globalStoryExtractionStage;
    private final ScenePlanningStage scenePlanningStage;
    private final ScriptYamlGenerationStage scriptYamlGenerationStage;
    private final YamlRepairStage yamlRepairStage;

    public ScriptAiPipelineService(NovelChapterParser chapterParser,
                                   ScriptYamlValidator yamlValidator,
                                   ScriptYamlDraftService ruleBasedDraftService,
                                   ChapterSummaryStage chapterSummaryStage,
                                   GlobalStoryExtractionStage globalStoryExtractionStage,
                                   ScenePlanningStage scenePlanningStage,
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
        this.scriptYamlGenerationStage = Objects.requireNonNull(
                scriptYamlGenerationStage,
                "ScriptYamlGenerationStage 不能为空"
        );
        this.yamlRepairStage = Objects.requireNonNull(yamlRepairStage, "YamlRepairStage 不能为空");
    }

    public ScriptAiPipelineResult generate(ScriptYamlGenerateRequest request) {
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

        try {
            context = summarizeChapters(context, chapters);
            context = extractGlobalStoryState(context);
            context = planScenes(context);
            context = generateYaml(context);
            return validateAndRepairIfNeeded(context, request);
        } catch (ScriptAiPipelineStageException ex) {
            return fallback(
                    request,
                    context,
                    "AI 阶段 " + ex.stage() + " 执行失败，已显式降级为规则版脚手架"
            );
        } catch (RuntimeException ex) {
            return fallback(
                    request,
                    context,
                    "AI 阶段 " + context.currentStage() + " 执行失败，已显式降级为规则版脚手架"
            );
        }
    }

    private PipelineContext summarizeChapters(PipelineContext context, List<NovelChapter> chapters) {
        List<ChapterSummary> summaries = new ArrayList<>(chapters.size());
        for (NovelChapter chapter : chapters) {
            try {
                summaries.add(chapterSummaryStage.summarize(chapter));
            } catch (RuntimeException ex) {
                throw new ScriptAiPipelineStageException(PipelineStage.CHAPTER_SUMMARY, ex);
            }
        }
        return context.withChapterSummaries(summaries);
    }

    private PipelineContext extractGlobalStoryState(PipelineContext context) {
        GlobalStoryState state;
        try {
            state = globalStoryExtractionStage.extract(context);
        } catch (RuntimeException ex) {
            throw new ScriptAiPipelineStageException(PipelineStage.GLOBAL_STORY_EXTRACTION, ex);
        }
        return context.withGlobalStoryState(state);
    }

    private PipelineContext planScenes(PipelineContext context) {
        ScenePlan plan;
        try {
            plan = scenePlanningStage.plan(context);
        } catch (RuntimeException ex) {
            throw new ScriptAiPipelineStageException(PipelineStage.SCENE_PLANNING, ex);
        }
        return context.withScenePlan(plan);
    }

    private PipelineContext generateYaml(PipelineContext context) {
        String yaml;
        try {
            yaml = scriptYamlGenerationStage.generate(context);
        } catch (RuntimeException ex) {
            throw new ScriptAiPipelineStageException(PipelineStage.SCRIPT_YAML_GENERATION, ex);
        }
        if (!hasText(yaml)) {
            throw new ScriptAiPipelineStageException(
                    PipelineStage.SCRIPT_YAML_GENERATION,
                    new IllegalStateException("AI YAML 生成结果为空")
            );
        }
        return context.withGeneratedYaml(yaml);
    }

    private ScriptAiPipelineResult validateAndRepairIfNeeded(PipelineContext context,
                                                             ScriptYamlGenerateRequest request) {
        ScriptYamlValidationResult validationResult = yamlValidator.validate(context.generatedYaml());
        context = context.withValidation(validationResult.status(), validationResult.errorMessage());
        if (validationResult.valid()) {
            return success(context.completed(), validationResult.cleanedYaml(), validationResult);
        }

        String repairedYaml;
        try {
            repairedYaml = yamlRepairStage.repair(context, validationResult);
        } catch (RuntimeException ex) {
            throw new ScriptAiPipelineStageException(PipelineStage.YAML_REPAIR, ex);
        }
        if (!hasText(repairedYaml)) {
            return fallback(request, context, "AI YAML 校验失败且修复结果为空，已显式降级为规则版脚手架");
        }
        context = context.withRepairedYaml(repairedYaml);

        ScriptYamlValidationResult repairedValidation = yamlValidator.validate(repairedYaml);
        context = context.withValidation(repairedValidation.status(), repairedValidation.errorMessage());
        if (repairedValidation.valid()) {
            return success(context.completed(), repairedValidation.cleanedYaml(), repairedValidation);
        }
        return fallback(request, context, "AI YAML 校验失败且自动修复未通过，已显式降级为规则版脚手架");
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
                                            String reason) {
        ScriptYamlGenerateResponse scaffold = ruleBasedDraftService.generateFromText(request);
        String fallbackYaml = markFallback(scaffold.yaml());
        ScriptYamlValidationResult validationResult = yamlValidator.validate(fallbackYaml);
        PipelineContext fallbackContext = context.withGeneratedYaml(fallbackYaml)
                .withValidation(validationResult.status(), validationResult.errorMessage())
                .completed();
        return new ScriptAiPipelineResult(
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
}
