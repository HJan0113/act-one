package com.hjan.actone.script.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptDialoguePatch;
import com.hjan.actone.script.model.ScriptDialogueScenePatch;
import com.hjan.actone.script.model.ScriptDialogueVoicePatch;
import com.hjan.actone.script.model.ScriptDialogueVoiceRewriteRequest;
import com.hjan.actone.script.model.ScriptDialogueVoiceRewriteResponse;
import com.hjan.actone.script.model.ScriptScenePatch;
import com.hjan.actone.script.model.ScriptSceneRefineRequest;
import com.hjan.actone.script.model.ScriptSceneRefineResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ScriptSceneRefinementService {

    private static final double REFINEMENT_TEMPERATURE = 0.2D;
    private static final double REFINEMENT_TOP_P = 0.75D;
    private static final int REFINEMENT_MAX_TOKENS = 2048;
    private static final Duration REFINEMENT_TIMEOUT = Duration.ofSeconds(90);
    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```[\\t ]*$");
    private static final String SCHEMA_SUMMARY = """
            只能修改单个 scene 的 title、location、time、scene_purpose、action、dialogues、ai_expansion。
            不得修改 id、act_id、chapter_index、source_chapter_id、source_excerpt。
            dialogues 每项包含 speaker、line，可包含 emotion；speaker 必须是已知角色名或“旁白”。
            应用 patch 后，完整 YAML 仍必须通过 screenplay_draft Schema v1 校验。
            """;

    private final ScriptDraftMapper scriptDraftMapper;
    private final ScriptProjectMapper scriptProjectMapper;
    private final ScriptYamlValidator scriptYamlValidator;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public ScriptSceneRefinementService(ScriptDraftMapper scriptDraftMapper,
                                        ScriptProjectMapper scriptProjectMapper,
                                        ScriptYamlValidator scriptYamlValidator,
                                        ObjectProvider<LLMService> llmServiceProvider,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper) {
        this.scriptDraftMapper = Objects.requireNonNull(scriptDraftMapper, "ScriptDraftMapper 不能为空");
        this.scriptProjectMapper = Objects.requireNonNull(scriptProjectMapper, "ScriptProjectMapper 不能为空");
        this.scriptYamlValidator = Objects.requireNonNull(scriptYamlValidator, "ScriptYamlValidator 不能为空");
        this.llmServiceProvider = Objects.requireNonNull(llmServiceProvider, "LLMService ObjectProvider 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    public ScriptSceneRefineResponse refine(ScriptSceneRefineRequest request) {
        String draftId = requireText(request.draftId(), "草稿 ID 不能为空。");
        String sceneId = requireText(request.sceneId(), "场景 ID 不能为空。");
        String instruction = requireText(request.instruction(), "精修指令不能为空。");

        ScriptDraftDO draft = loadOwnedDraft(draftId);
        Map<String, Object> document = parseYamlDocument(draft.getYamlContent());
        Map<String, Object> scene = findScene(document, sceneId);
        ScriptScenePatch patch = normalizePatch(createPatch(sceneId, instruction, document, scene), sceneId);

        applyPatch(scene, patch);
        String patchedYaml = dumpYaml(document);
        ScriptYamlValidationResult validation = scriptYamlValidator.validate(patchedYaml);
        return new ScriptSceneRefineResponse(
                draft.getId(),
                sceneId,
                patch,
                validation.cleanedYaml(),
                validation.status(),
                validation.errorMessage()
        );
    }

    public ScriptDialogueVoiceRewriteResponse rewriteDialogueVoice(ScriptDialogueVoiceRewriteRequest request) {
        String draftId = requireText(request.draftId(), "草稿 ID 不能为空。");
        String speaker = requireText(request.speaker(), "说话人不能为空。");
        String instruction = requireText(request.instruction(), "口吻重写指令不能为空。");

        ScriptDraftDO draft = loadOwnedDraft(draftId);
        Map<String, Object> document = parseYamlDocument(draft.getYamlContent());
        List<Map<String, Object>> targetScenes = findDialogueTargetScenes(document, speaker, request.sceneIds());
        ScriptDialogueVoicePatch voicePatch = normalizeVoicePatch(
                createVoicePatch(speaker, instruction, document, targetScenes),
                speaker
        );

        applyVoicePatch(document, voicePatch);
        String patchedYaml = dumpYaml(document);
        ScriptYamlValidationResult validation = scriptYamlValidator.validate(patchedYaml);
        return new ScriptDialogueVoiceRewriteResponse(
                draft.getId(),
                speaker,
                voicePatch,
                validation.cleanedYaml(),
                validation.status(),
                validation.errorMessage()
        );
    }

    private ScriptScenePatch createPatch(String sceneId,
                                         String instruction,
                                         Map<String, Object> document,
                                         Map<String, Object> scene) {
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return deterministicPatch(sceneId, instruction, scene);
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.SCENE_REFINEMENT,
                Map.of(
                        "scene_id", sceneId,
                        "instruction", instruction,
                        "scene_yaml", dumpYaml(scene),
                        "characters_yaml", dumpYaml(document.getOrDefault("characters", List.of())),
                        "schema_summary", SCHEMA_SUMMARY
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                REFINEMENT_TEMPERATURE,
                REFINEMENT_TOP_P,
                REFINEMENT_MAX_TOKENS,
                REFINEMENT_TIMEOUT,
                ChatResponseFormat.JSON_OBJECT
        ));
        return parsePatch(response);
    }

    private ScriptDialogueVoicePatch createVoicePatch(String speaker,
                                                      String instruction,
                                                      Map<String, Object> document,
                                                      List<Map<String, Object>> targetScenes) {
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return deterministicVoicePatch(speaker, instruction, targetScenes);
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.DIALOGUE_VOICE_REWRITE,
                Map.of(
                        "speaker", speaker,
                        "instruction", instruction,
                        "scenes_yaml", dumpYaml(targetScenes),
                        "characters_yaml", dumpYaml(document.getOrDefault("characters", List.of())),
                        "relationships_yaml", dumpYaml(document.getOrDefault("relationships", List.of())),
                        "schema_summary", SCHEMA_SUMMARY
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                REFINEMENT_TEMPERATURE,
                REFINEMENT_TOP_P,
                REFINEMENT_MAX_TOKENS,
                REFINEMENT_TIMEOUT,
                ChatResponseFormat.JSON_OBJECT
        ));
        return parseVoicePatch(response);
    }

    private ScriptDialogueVoicePatch deterministicVoicePatch(String speaker,
                                                             String instruction,
                                                             List<Map<String, Object>> targetScenes) {
        List<ScriptDialogueScenePatch> scenePatches = targetScenes.stream()
                .map(scene -> new ScriptDialogueScenePatch(
                        optionalText(scene, "id"),
                        readDialoguePatches(scene).stream()
                                .map(dialogue -> speaker.equals(dialogue.speaker())
                                        ? new ScriptDialoguePatch(
                                        dialogue.speaker(),
                                        dialogue.line() + "（口吻指令：" + instruction + "）",
                                        dialogue.emotion()
                                )
                                        : dialogue)
                                .toList(),
                        "AI 服务不可用，已生成规则兜底对白口吻补丁。",
                        List.of("规则兜底未调用模型，请作者复核人物口吻。")
                ))
                .toList();
        return new ScriptDialogueVoicePatch(
                speaker,
                speaker + "：" + instruction,
                scenePatches,
                "AI 服务不可用，已按规则生成多场景对白补丁。",
                List.of("规则兜底未调用模型，请作者复核。")
        );
    }

    private ScriptScenePatch deterministicPatch(String sceneId, String instruction, Map<String, Object> scene) {
        String action = optionalText(scene, "action");
        String nextAction = StringUtils.hasText(action)
                ? action + "\n（局部精修指令：" + instruction + "）"
                : "局部精修指令：" + instruction;
        return new ScriptScenePatch(
                sceneId,
                null,
                null,
                null,
                null,
                nextAction,
                null,
                true,
                "AI 服务不可用，已生成规则兜底动作补丁。",
                List.of("规则兜底未调用模型，请作者复核动作表达。")
        );
    }

    private ScriptScenePatch parsePatch(ChatResponse response) {
        if (response == null || !StringUtils.hasText(response.content())) {
            throw new RemoteException("AI 局部精修返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        String json = response.content()
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        json = LEADING_CODE_FENCE.matcher(json).replaceFirst("");
        json = TRAILING_CODE_FENCE.matcher(json).replaceFirst("");
        try {
            return objectMapper.readValue(json, ScriptScenePatch.class);
        } catch (JsonProcessingException ex) {
            throw new RemoteException("AI 局部精修 JSON 解析失败", ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private ScriptDialogueVoicePatch parseVoicePatch(ChatResponse response) {
        if (response == null || !StringUtils.hasText(response.content())) {
            throw new RemoteException("AI 对白口吻重写返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        String json = response.content()
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        json = LEADING_CODE_FENCE.matcher(json).replaceFirst("");
        json = TRAILING_CODE_FENCE.matcher(json).replaceFirst("");
        try {
            return objectMapper.readValue(json, ScriptDialogueVoicePatch.class);
        } catch (JsonProcessingException ex) {
            throw new RemoteException("AI 对白口吻重写 JSON 解析失败", ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private ScriptScenePatch normalizePatch(ScriptScenePatch patch, String sceneId) {
        if (patch == null) {
            throw new RemoteException("AI 局部精修 patch 为空", BaseErrorCode.REMOTE_ERROR);
        }
        if (StringUtils.hasText(patch.sceneId()) && !sceneId.equals(patch.sceneId().trim())) {
            throw new RemoteException("AI 局部精修返回了错误 sceneId：" + patch.sceneId(), BaseErrorCode.REMOTE_ERROR);
        }
        return new ScriptScenePatch(
                sceneId,
                patch.title(),
                patch.location(),
                patch.time(),
                patch.scenePurpose(),
                patch.action(),
                patch.dialogues(),
                patch.aiExpansion() == null ? Boolean.TRUE : patch.aiExpansion(),
                patch.summary(),
                patch.riskWarnings() == null ? List.of() : patch.riskWarnings()
        );
    }

    private ScriptDialogueVoicePatch normalizeVoicePatch(ScriptDialogueVoicePatch patch, String speaker) {
        if (patch == null || patch.scenePatches() == null || patch.scenePatches().isEmpty()) {
            throw new RemoteException("AI 对白口吻重写 patch 为空", BaseErrorCode.REMOTE_ERROR);
        }
        if (StringUtils.hasText(patch.speaker()) && !speaker.equals(patch.speaker().trim())) {
            throw new RemoteException("AI 对白口吻重写返回了错误 speaker：" + patch.speaker(), BaseErrorCode.REMOTE_ERROR);
        }
        return new ScriptDialogueVoicePatch(
                speaker,
                patch.voiceSample(),
                patch.scenePatches(),
                patch.summary(),
                patch.riskWarnings() == null ? List.of() : patch.riskWarnings()
        );
    }

    private void applyPatch(Map<String, Object> scene, ScriptScenePatch patch) {
        putTextIfPresent(scene, "title", patch.title());
        putTextIfPresent(scene, "location", patch.location());
        putTextIfPresent(scene, "time", patch.time());
        putTextIfPresent(scene, "scene_purpose", patch.scenePurpose());
        putTextIfPresent(scene, "action", patch.action());
        if (patch.dialogues() != null) {
            scene.put("dialogues", toDialogueMaps(patch.dialogues()));
        }
        scene.put("ai_expansion", patch.aiExpansion());
    }

    private void applyVoicePatch(Map<String, Object> document, ScriptDialogueVoicePatch patch) {
        for (ScriptDialogueScenePatch scenePatch : patch.scenePatches()) {
            String sceneId = requireText(scenePatch.sceneId(), "scenePatches[].sceneId 不能为空。");
            Map<String, Object> scene = findScene(document, sceneId);
            if (scenePatch.dialogues() == null) {
                throw new ClientException("scenePatches[" + sceneId + "].dialogues 不能为空。");
            }
            scene.put("dialogues", toDialogueMaps(scenePatch.dialogues()));
            scene.put("ai_expansion", true);
        }
    }

    private List<Map<String, Object>> toDialogueMaps(List<ScriptDialoguePatch> dialogues) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < dialogues.size(); index++) {
            ScriptDialoguePatch dialogue = dialogues.get(index);
            String speaker = requireText(dialogue.speaker(), "dialogues[" + index + "].speaker 不能为空。");
            String line = requireText(dialogue.line(), "dialogues[" + index + "].line 不能为空。");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("speaker", speaker);
            if (StringUtils.hasText(dialogue.emotion())) {
                value.put("emotion", dialogue.emotion().trim());
            }
            value.put("line", line);
            values.add(value);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<ScriptDialoguePatch> readDialoguePatches(Map<String, Object> scene) {
        Object dialogues = scene.get("dialogues");
        if (!(dialogues instanceof List<?> dialogueList)) {
            return List.of();
        }
        List<ScriptDialoguePatch> values = new ArrayList<>();
        for (Object value : dialogueList) {
            if (value instanceof Map<?, ?> dialogue) {
                values.add(new ScriptDialoguePatch(
                        optionalText(dialogue, "speaker"),
                        optionalText(dialogue, "line"),
                        optionalText(dialogue, "emotion")
                ));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlDocument(String yamlContent) {
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlContent);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            throw new ClientException("YAML 顶层结构必须是对象。");
        } catch (YAMLException ex) {
            throw new ClientException("草稿 YAML 无法解析，不能执行局部精修。", ex, BaseErrorCode.CLIENT_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findScene(Map<String, Object> document, String sceneId) {
        Object scenes = document.get("scenes");
        if (!(scenes instanceof List<?> sceneList)) {
            throw new ClientException("YAML scenes 必须是数组。");
        }
        for (Object value : sceneList) {
            if (value instanceof Map<?, ?> scene && sceneId.equals(optionalText(scene, "id"))) {
                return (Map<String, Object>) scene;
            }
        }
        throw new ClientException("场景不存在：" + sceneId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findDialogueTargetScenes(Map<String, Object> document,
                                                               String speaker,
                                                               List<String> sceneIds) {
        Object scenes = document.get("scenes");
        if (!(scenes instanceof List<?> sceneList)) {
            throw new ClientException("YAML scenes 必须是数组。");
        }
        List<Map<String, Object>> selected = new ArrayList<>();
        for (Object value : sceneList) {
            if (!(value instanceof Map<?, ?> scene)) {
                continue;
            }
            Map<String, Object> sceneMap = (Map<String, Object>) scene;
            String sceneId = optionalText(sceneMap, "id");
            boolean selectedById = sceneIds != null && !sceneIds.isEmpty() && sceneIds.contains(sceneId);
            boolean selectedBySpeaker = (sceneIds == null || sceneIds.isEmpty()) && sceneContainsSpeaker(sceneMap, speaker);
            if (selectedById || selectedBySpeaker) {
                selected.add(sceneMap);
            }
        }
        if (selected.isEmpty()) {
            throw new ClientException("未找到包含说话人 “" + speaker + "” 的场景。");
        }
        return selected;
    }

    private boolean sceneContainsSpeaker(Map<String, Object> scene, String speaker) {
        return readDialoguePatches(scene).stream().anyMatch(dialogue -> speaker.equals(dialogue.speaker()));
    }

    private String dumpYaml(Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        return new Yaml(options).dump(value).strip();
    }

    private ScriptDraftDO loadOwnedDraft(String draftId) {
        ScriptDraftDO draft = scriptDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new ClientException("草稿不存在。");
        }
        verifyProjectOwned(draft.getProjectId());
        return draft;
    }

    private void verifyProjectOwned(String projectId) {
        ScriptProjectDO project = scriptProjectMapper.selectOne(new LambdaQueryWrapper<ScriptProjectDO>()
                .eq(ScriptProjectDO::getId, projectId)
                .eq(ScriptProjectDO::getCreatedBy, UserContext.requireUser().userId()));
        if (project == null) {
            throw new ClientException("项目不存在或无权访问");
        }
    }

    private void putTextIfPresent(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException(message);
        }
        return value.trim();
    }

    private String optionalText(Map<?, ?> source, String key) {
        Object value = source.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
