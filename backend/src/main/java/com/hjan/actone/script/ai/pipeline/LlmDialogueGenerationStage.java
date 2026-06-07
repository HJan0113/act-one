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
import com.hjan.actone.script.ai.model.DialogueDraft;
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

public class LlmDialogueGenerationStage implements DialogueGenerationStage {

    private static final String NARRATOR = "旁白";
    private static final double DIALOGUE_TEMPERATURE = 0.3D;
    private static final double DIALOGUE_TOP_P = 0.85D;
    private static final int DIALOGUE_MAX_TOKENS = 4096;
    private static final Duration DEFAULT_DIALOGUE_TIMEOUT = Duration.ofSeconds(90);

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public LlmDialogueGenerationStage(LLMService llmService,
                                      PromptTemplateService promptTemplateService,
                                      ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, DEFAULT_DIALOGUE_TIMEOUT);
    }

    public LlmDialogueGenerationStage(LLMService llmService,
                                      PromptTemplateService promptTemplateService,
                                      ObjectMapper objectMapper,
                                      Duration timeout) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_DIALOGUE_TIMEOUT, "对白生成 timeout");
    }

    @Override
    public DialogueDraft draft(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.globalStoryState().characters().isEmpty()) {
            throw new IllegalArgumentException("globalStoryState 不能为空");
        }
        if (context.scenePlan() == null || context.scenePlan().scenes().isEmpty()) {
            throw new IllegalArgumentException("scenePlan 不能为空");
        }
        if (context.cinematicTreatment() == null || context.cinematicTreatment().scenes().isEmpty()) {
            throw new IllegalArgumentException("cinematicTreatment 不能为空");
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.DIALOGUE_GENERATION,
                Map.of(
                        "global_story_state", serialize(context.globalStoryState(), "全局故事状态序列化失败"),
                        "scene_plan", serialize(context.scenePlan(), "场景规划序列化失败"),
                        "cinematic_treatment", serialize(context.cinematicTreatment(), "心理影视化结果序列化失败")
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                DIALOGUE_TEMPERATURE,
                DIALOGUE_TOP_P,
                DIALOGUE_MAX_TOKENS,
                timeout,
                ChatResponseFormat.JSON_OBJECT
        ));
        DialogueDraft draft = parseDraft(response);
        validateDraft(context, draft);
        return draft;
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private DialogueDraft parseDraft(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 剧本对白返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            return objectMapper.readValue(response.content(), DialogueDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new RemoteException("AI 剧本对白结构非法", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateDraft(PipelineContext context, DialogueDraft draft) {
        Map<String, ScenePlan.Scene> sceneById = sceneById(context.scenePlan());
        Map<String, CharacterInfo> characterById = characterById(context.globalStoryState());
        Set<String> treatedSceneIds = new HashSet<>();

        for (DialogueDraft.SceneDialogues sceneDialogues : draft.scenes()) {
            ScenePlan.Scene plannedScene = sceneById.get(sceneDialogues.sceneId());
            if (plannedScene == null) {
                throw new RemoteException("AI 剧本对白场景引用非法", BaseErrorCode.REMOTE_ERROR);
            }
            if (!treatedSceneIds.add(sceneDialogues.sceneId())) {
                throw new RemoteException("AI 剧本对白场景重复", BaseErrorCode.REMOTE_ERROR);
            }
            if (!plannedScene.sourceChapterId().equals(sceneDialogues.sourceChapterId())
                    || !plannedScene.chapterIndex().equals(sceneDialogues.chapterIndex())) {
                throw new RemoteException("AI 剧本对白场景来源不一致", BaseErrorCode.REMOTE_ERROR);
            }
            Map<String, Set<String>> linesBySpeaker = new HashMap<>();
            for (DialogueDraft.DialogueLine line : sceneDialogues.dialogues()) {
                validateLine(plannedScene, characterById, line);
                if (!NARRATOR.equals(line.speaker())) {
                    linesBySpeaker.computeIfAbsent(line.speaker(), ignored -> new HashSet<>())
                            .add(normalize(line.line()));
                }
            }
            validateVoiceDistinctness(linesBySpeaker);
        }

        for (String sceneId : sceneById.keySet()) {
            if (!treatedSceneIds.contains(sceneId)) {
                throw new RemoteException("AI 剧本对白缺少场景对白", BaseErrorCode.REMOTE_ERROR);
            }
        }
    }

    private void validateVoiceDistinctness(Map<String, Set<String>> linesBySpeaker) {
        if (linesBySpeaker.size() <= 1) {
            return;
        }
        Set<String> distinctLines = new HashSet<>();
        linesBySpeaker.values().forEach(distinctLines::addAll);
        if (distinctLines.size() <= 1) {
            throw new RemoteException("AI 剧本对白缺少角色区分", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateLine(ScenePlan.Scene plannedScene,
                              Map<String, CharacterInfo> characterById,
                              DialogueDraft.DialogueLine line) {
        if (NARRATOR.equals(line.speaker())) {
            return;
        }
        if (!hasText(line.speakerCharacterId())) {
            throw new RemoteException("AI 剧本对白缺少 speakerCharacterId", BaseErrorCode.REMOTE_ERROR);
        }
        CharacterInfo character = characterById.get(line.speakerCharacterId());
        if (character == null) {
            throw new RemoteException("AI 剧本对白人物引用非法", BaseErrorCode.REMOTE_ERROR);
        }
        if (!plannedScene.characters().contains(line.speakerCharacterId())) {
            throw new RemoteException("AI 剧本对白人物不在场景中", BaseErrorCode.REMOTE_ERROR);
        }
        if (!character.names().contains(line.speaker())) {
            throw new RemoteException("AI 剧本对白 speaker 不一致", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private Map<String, ScenePlan.Scene> sceneById(ScenePlan scenePlan) {
        Map<String, ScenePlan.Scene> result = new LinkedHashMap<>();
        for (ScenePlan.Scene scene : scenePlan.scenes()) {
            result.put(scene.id(), scene);
        }
        return result;
    }

    private Map<String, CharacterInfo> characterById(GlobalStoryState state) {
        Map<String, CharacterInfo> result = new HashMap<>();
        for (GlobalStoryState.CharacterProfile character : state.characters()) {
            Set<String> names = new HashSet<>();
            names.add(character.name());
            names.addAll(character.aliases());
            result.put(character.id(), new CharacterInfo(names));
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CharacterInfo(Set<String> names) {
    }
}
