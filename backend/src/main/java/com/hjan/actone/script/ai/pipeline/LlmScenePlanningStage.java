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
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LlmScenePlanningStage implements ScenePlanningStage {

    private static final double PLANNING_TEMPERATURE = 0.2D;
    private static final double PLANNING_TOP_P = 0.8D;
    private static final int PLANNING_MAX_TOKENS = 4096;
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
        if (context.globalStoryState().characters().isEmpty()
                || context.globalStoryState().locations().isEmpty()
                || context.globalStoryState().timeline().isEmpty()) {
            throw new IllegalArgumentException("globalStoryState 不能为空");
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.SCENE_PLANNING,
                Map.of(
                        "chapter_summaries", serialize(context.chapterSummaries(), "章节摘要序列化失败"),
                        "global_story_state", serialize(context.globalStoryState(), "全局故事状态序列化失败")
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
        try {
            return objectMapper.readValue(response.content(), ScenePlan.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new RemoteException("AI 场景规划结构非法", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validatePlan(PipelineContext context, ScenePlan plan) {
        Map<String, Integer> chapterIndexById = chapterIndexById(context.chapterSummaries());
        Set<String> characterIds = characterIds(context.globalStoryState());
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
            validateCharacters(characterIds, scene.characters());
            scene.beats().forEach(beat -> validateCharacters(characterIds, beat.characterIds()));
            sceneCountByChapter.merge(scene.sourceChapterId(), 1, Integer::sum);
        }

        for (ChapterSummary summary : context.chapterSummaries()) {
            if (!sceneCountByChapter.containsKey(summary.chapterId())) {
                throw new RemoteException("AI 场景规划缺少章节场景", BaseErrorCode.REMOTE_ERROR);
            }
        }
        if (sceneCountByChapter.values().stream().noneMatch(count -> count > 1)) {
            throw new RemoteException("AI 场景规划未拆分章节场景", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private Map<String, Integer> chapterIndexById(List<ChapterSummary> summaries) {
        Map<String, Integer> result = new HashMap<>();
        for (ChapterSummary summary : summaries) {
            result.put(summary.chapterId(), summary.chapterIndex());
        }
        return result;
    }

    private Set<String> characterIds(GlobalStoryState state) {
        Set<String> result = new HashSet<>();
        for (GlobalStoryState.CharacterProfile character : state.characters()) {
            result.add(character.id());
        }
        return result;
    }

    private void validateStableSceneId(ScenePlan.Scene scene, int order) {
        String expected = "scene_%03d".formatted(order);
        if (!expected.equals(scene.id())) {
            throw new RemoteException("AI 场景规划场景 ID 不稳定", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateCharacters(Set<String> knownCharacterIds, List<String> actualCharacterIds) {
        for (String characterId : actualCharacterIds) {
            if (!knownCharacterIds.contains(characterId)) {
                throw new RemoteException("AI 场景规划人物引用非法", BaseErrorCode.REMOTE_ERROR);
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
