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
import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LlmCinematicTreatmentStage implements CinematicTreatmentStage {

    private static final double TREATMENT_TEMPERATURE = 0.2D;
    private static final double TREATMENT_TOP_P = 0.8D;
    private static final int TREATMENT_MAX_TOKENS = 4096;
    private static final Duration DEFAULT_TREATMENT_TIMEOUT = Duration.ofSeconds(90);

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public LlmCinematicTreatmentStage(LLMService llmService,
                                      PromptTemplateService promptTemplateService,
                                      ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, DEFAULT_TREATMENT_TIMEOUT);
    }

    public LlmCinematicTreatmentStage(LLMService llmService,
                                      PromptTemplateService promptTemplateService,
                                      ObjectMapper objectMapper,
                                      Duration timeout) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_TREATMENT_TIMEOUT, "心理影视化 timeout");
    }

    @Override
    public CinematicTreatment visualize(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.chapterSummaries().isEmpty()) {
            throw new IllegalArgumentException("chapterSummaries 不能为空");
        }
        if (context.globalStoryState().characters().isEmpty()) {
            throw new IllegalArgumentException("globalStoryState 不能为空");
        }
        if (context.scenePlan() == null || context.scenePlan().scenes().isEmpty()) {
            throw new IllegalArgumentException("scenePlan 不能为空");
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.CINEMATIC_TREATMENT,
                Map.of(
                        "chapter_summaries", serialize(context.chapterSummaries(), "章节摘要序列化失败"),
                        "global_story_state", serialize(context.globalStoryState(), "全局故事状态序列化失败"),
                        "scene_plan", serialize(context.scenePlan(), "场景规划序列化失败")
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                TREATMENT_TEMPERATURE,
                TREATMENT_TOP_P,
                TREATMENT_MAX_TOKENS,
                timeout,
                ChatResponseFormat.JSON_OBJECT
        ));
        CinematicTreatment treatment = parseTreatment(response);
        validateTreatment(context, treatment);
        return treatment;
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private CinematicTreatment parseTreatment(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 心理影视化返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            return objectMapper.readValue(response.content(), CinematicTreatment.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new RemoteException("AI 心理影视化结构非法", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateTreatment(PipelineContext context, CinematicTreatment treatment) {
        Map<String, ScenePlan.Scene> sceneById = sceneById(context.scenePlan());
        Set<String> characterIds = characterIds(context.globalStoryState());
        Set<String> treatedSceneIds = new HashSet<>();
        int transformationCount = 0;

        for (CinematicTreatment.SceneTreatment sceneTreatment : treatment.scenes()) {
            ScenePlan.Scene plannedScene = sceneById.get(sceneTreatment.sceneId());
            if (plannedScene == null) {
                throw new RemoteException("AI 心理影视化场景引用非法", BaseErrorCode.REMOTE_ERROR);
            }
            if (!treatedSceneIds.add(sceneTreatment.sceneId())) {
                throw new RemoteException("AI 心理影视化场景重复", BaseErrorCode.REMOTE_ERROR);
            }
            if (!plannedScene.sourceChapterId().equals(sceneTreatment.sourceChapterId())
                    || !plannedScene.chapterIndex().equals(sceneTreatment.chapterIndex())) {
                throw new RemoteException("AI 心理影视化场景来源不一致", BaseErrorCode.REMOTE_ERROR);
            }
            if (containsInteriorNarration(sceneTreatment.action())) {
                throw new RemoteException("AI 心理影视化仍包含不可拍心理", BaseErrorCode.REMOTE_ERROR);
            }

            for (CinematicTreatment.Transformation transformation : sceneTreatment.transformations()) {
                transformationCount++;
                validateTransformation(characterIds, transformation);
            }
        }

        for (String sceneId : sceneById.keySet()) {
            if (!treatedSceneIds.contains(sceneId)) {
                throw new RemoteException("AI 心理影视化缺少场景处理", BaseErrorCode.REMOTE_ERROR);
            }
        }
        if (transformationCount == 0) {
            throw new RemoteException("AI 心理影视化缺少改写对照", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private Map<String, ScenePlan.Scene> sceneById(ScenePlan scenePlan) {
        Map<String, ScenePlan.Scene> result = new LinkedHashMap<>();
        for (ScenePlan.Scene scene : scenePlan.scenes()) {
            result.put(scene.id(), scene);
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

    private void validateTransformation(Set<String> knownCharacterIds,
                                        CinematicTreatment.Transformation transformation) {
        if (transformation.characterIds().isEmpty()) {
            throw new RemoteException("AI 心理影视化缺少人物引用", BaseErrorCode.REMOTE_ERROR);
        }
        for (String characterId : transformation.characterIds()) {
            if (!knownCharacterIds.contains(characterId)) {
                throw new RemoteException("AI 心理影视化人物引用非法", BaseErrorCode.REMOTE_ERROR);
            }
        }
        if (normalize(transformation.originalText()).equals(normalize(transformation.visualizedText()))) {
            throw new RemoteException("AI 心理影视化缺少有效改写", BaseErrorCode.REMOTE_ERROR);
        }
        if (containsInteriorNarration(transformation.visualizedText())) {
            throw new RemoteException("AI 心理影视化仍包含不可拍心理", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private boolean containsInteriorNarration(String value) {
        String normalized = normalize(value);
        return normalized.contains("心想")
                || normalized.contains("内心")
                || normalized.contains("意识到")
                || normalized.contains("觉得")
                || normalized.contains("感到");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
