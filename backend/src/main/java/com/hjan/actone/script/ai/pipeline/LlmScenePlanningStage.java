package com.hjan.actone.script.ai.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.ai.prompt.PromptTemplateId;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.prompt.RenderedPrompt;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LlmScenePlanningStage implements ScenePlanningStage {

    private static final double PLANNING_TEMPERATURE = 0.2D;
    private static final double PLANNING_TOP_P = 0.8D;
    private static final int PLANNING_MAX_TOKENS = 16384;
    private static final Duration DEFAULT_PLANNING_TIMEOUT = Duration.ofSeconds(90);

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public LlmScenePlanningStage(LLMService llmService,
                                 PromptTemplateService promptTemplateService,
                                 ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, DEFAULT_PLANNING_TIMEOUT);
    }

    public LlmScenePlanningStage(LLMService llmService,
                                 PromptTemplateService promptTemplateService,
                                 ObjectMapper objectMapper,
                                 Duration timeout) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_PLANNING_TIMEOUT, "场景规划 timeout");
    }

    @Override
    public ScenePlan plan(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.chapterSummaries().isEmpty()) {
            throw new IllegalArgumentException("chapterSummaries 不能为空");
        }

        long startedNanos = System.nanoTime();
        log.info("开始场景规划 taskId={} 章节数={}", context.taskId(), context.chapterSummaries().size());
        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.SCENE_PLANNING,
                Map.of(
                        "chapter_summaries", serialize(context.chapterSummaries(), "章节摘要序列化失败")
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                PLANNING_TEMPERATURE,
                PLANNING_TOP_P,
                PLANNING_MAX_TOKENS,
                timeout,
                ChatResponseFormat.JSON_OBJECT
        ));
        ScenePlan plan = parsePlan(response);
        validatePlan(context, plan);
        log.info("完成场景规划 taskId={} 耗时={}ms 场景数={} 响应模型={}",
                context.taskId(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                plan.scenes().size(),
                response.model());
        return plan;
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private ScenePlan parsePlan(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 场景规划返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        if ("LENGTH".equalsIgnoreCase(response.finishReason())) {
            log.warn("场景规划输出被截断 响应长度={}", response.content().length());
            throw new RemoteException("AI 场景规划输出被截断", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            return objectMapper.readValue(response.content(), ScenePlan.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new RemoteException("AI 场景规划结构非法", ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validatePlan(PipelineContext context, ScenePlan plan) {
        Map<String, Integer> chapterIndexById = chapterIndexById(context.chapterSummaries());
        Map<String, Integer> sceneCountByChapter = new LinkedHashMap<>();

        for (int i = 0; i < plan.scenes().size(); i++) {
            ScenePlan.Scene scene = plan.scenes().get(i);
            validateStableSceneId(scene, i + 1);

            Integer expectedChapterIndex = chapterIndexById.get(scene.sourceChapterId());
            if (expectedChapterIndex == null) {
                throw new RemoteException("AI 场景规划来源章节不一致", BaseErrorCode.REMOTE_ERROR);
            }
            if (!expectedChapterIndex.equals(scene.chapterIndex())) {
                throw new RemoteException("AI 场景规划章节索引不一致", BaseErrorCode.REMOTE_ERROR);
            }

            if (scene.characters().isEmpty()) {
                throw new RemoteException("AI 场景规划缺少场景人物", BaseErrorCode.REMOTE_ERROR);
            }
            sceneCountByChapter.merge(scene.sourceChapterId(), 1, Integer::sum);
        }

        for (ChapterSummary summary : context.chapterSummaries()) {
            if (!sceneCountByChapter.containsKey(summary.chapterId())) {
                throw new RemoteException("AI 场景规划缺少章节场景", BaseErrorCode.REMOTE_ERROR);
            }
        }
    }

    private Map<String, Integer> chapterIndexById(List<ChapterSummary> summaries) {
        Map<String, Integer> result = new HashMap<>();
        for (ChapterSummary summary : summaries) {
            result.put(summary.chapterId(), summary.chapterIndex());
        }
        return result;
    }

    private void validateStableSceneId(ScenePlan.Scene scene, int order) {
        String expected = "scene_%03d".formatted(order);
        if (!expected.equals(scene.id())) {
            throw new RemoteException("AI 场景规划场景 ID 不稳定", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
