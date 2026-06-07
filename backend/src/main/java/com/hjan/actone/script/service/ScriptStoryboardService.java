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
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptStoryboardResponse;
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
public class ScriptStoryboardService {

    private static final double STORYBOARD_TEMPERATURE = 0.6D;
    private static final double STORYBOARD_TOP_P = 0.8D;
    private static final int STORYBOARD_MAX_TOKENS = 2048;
    private static final Duration STORYBOARD_TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```[\\t ]*$");

    private final ScriptDraftMapper scriptDraftMapper;
    private final ScriptProjectMapper scriptProjectMapper;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public ScriptStoryboardService(ScriptDraftMapper scriptDraftMapper,
                                   ScriptProjectMapper scriptProjectMapper,
                                   ObjectProvider<LLMService> llmServiceProvider,
                                   PromptTemplateService promptTemplateService,
                                   ObjectMapper objectMapper) {
        this.scriptDraftMapper = Objects.requireNonNull(scriptDraftMapper);
        this.scriptProjectMapper = Objects.requireNonNull(scriptProjectMapper);
        this.llmServiceProvider = Objects.requireNonNull(llmServiceProvider);
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ScriptStoryboardResponse generate(String draftId, String sceneId, int shotCount) {
        ScriptDraftDO draft = loadOwnedDraft(draftId);
        String sceneYaml = extractSceneYaml(draft.getYamlContent(), sceneId);
        String charactersYaml = extractCharactersYaml(draft.getYamlContent());
        int effectiveShotCount = Math.max(3, Math.min(6, shotCount <= 0 ? 4 : shotCount));

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return buildFallbackResponse(draftId, sceneId, effectiveShotCount, sceneYaml);
        }

        try {
            RenderedPrompt prompt = promptTemplateService.render(
                    PromptTemplateId.STORYBOARD_SUGGESTION,
                    Map.of(
                            "scene_yaml", sceneYaml,
                            "characters_yaml", charactersYaml,
                            "shot_count", String.valueOf(effectiveShotCount)
                    )
            );
            ChatResponse response = llmService.chat(new ChatRequest(
                    List.of(ChatMessage.user(prompt.content())),
                    null,
                    STORYBOARD_TEMPERATURE,
                    STORYBOARD_TOP_P,
                    STORYBOARD_MAX_TOKENS,
                    STORYBOARD_TIMEOUT,
                    ChatResponseFormat.JSON_OBJECT
            ));
            StoryboardAiResult aiResult = parseAiResult(response);
            return new ScriptStoryboardResponse(draftId, sceneId, "ai_assisted",
                    aiResult.shots(), aiResult.suggestions());
        } catch (RuntimeException ex) {
            return buildFallbackResponse(draftId, sceneId, effectiveShotCount, sceneYaml);
        }
    }

    private ScriptStoryboardResponse buildFallbackResponse(String draftId, String sceneId,
                                                            int shotCount, String sceneYaml) {
        List<ScriptStoryboardResponse.ShotSuggestion> shots = new ArrayList<>();
        for (int i = 1; i <= shotCount; i++) {
            shots.add(new ScriptStoryboardResponse.ShotSuggestion(
                    "shot_" + String.format("%02d", i),
                    i == 1 ? "全景" : i == shotCount ? "特写" : "中景",
                    "主体待定",
                    "基于场景 action 的动作描述",
                    "中性",
                    "待补充",
                    "环境音 / 对白",
                    "自然光 / 冷调",
                    "规则兜底镜头建议，AI 可用时替换。"
            ));
        }
        List<String> suggestions = List.of(
                "当前为规则兜底分镜，AI 模型可用时会生成更精准的镜头建议。",
                "建议补充场景地点、时间和人物站位后再请求 AI 分镜。"
        );
        return new ScriptStoryboardResponse(draftId, sceneId, "rule_based_fallback", shots, suggestions);
    }

    private StoryboardAiResult parseAiResult(ChatResponse response) {
        if (response == null || !StringUtils.hasText(response.content())) {
            throw new ClientException("AI 分镜建议返回为空。");
        }
        String json = response.content()
                .replace("﻿", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        json = LEADING_CODE_FENCE.matcher(json).replaceFirst("");
        json = TRAILING_CODE_FENCE.matcher(json).replaceFirst("");
        try {
            StoryboardAiResult result = objectMapper.readValue(json, StoryboardAiResult.class);
            return result == null ? new StoryboardAiResult(List.of(), List.of()) : result;
        } catch (JsonProcessingException ex) {
            throw new ClientException("AI 分镜建议 JSON 解析失败。", ex, BaseErrorCode.CLIENT_ERROR);
        }
    }

    private String extractSceneYaml(String fullYaml, String sceneId) {
        if (!StringUtils.hasText(fullYaml) || !StringUtils.hasText(sceneId)) {
            return "scenes: []";
        }
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(fullYaml);
            if (!(parsed instanceof Map<?, ?> document)) {
                return "scenes: []";
            }
            Object scenes = document.get("scenes");
            if (!(scenes instanceof List<?> sceneList)) {
                return "scenes: []";
            }
            for (Object value : sceneList) {
                if (value instanceof Map<?, ?> scene && sceneId.equals(optionalText(scene, "id"))) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("scenes", List.of(scene));
                    return dumpYaml(result);
                }
            }
            return "scenes: []";
        } catch (YAMLException ex) {
            throw new ClientException("草稿 YAML 无法解析，不能生成分镜建议。", ex, BaseErrorCode.CLIENT_ERROR);
        }
    }

    private String extractCharactersYaml(String fullYaml) {
        if (!StringUtils.hasText(fullYaml)) {
            return "characters: []";
        }
        String[] lines = fullYaml.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        StringBuilder builder = new StringBuilder();
        boolean inCharacters = false;
        for (String line : lines) {
            if (!inCharacters) {
                if (line.matches("^characters:\\s*$")) {
                    inCharacters = true;
                    builder.append(line).append('\n');
                }
                continue;
            }
            if (!line.startsWith("  ") && !line.trim().isEmpty()) {
                break;
            }
            builder.append(line).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private ScriptDraftDO loadOwnedDraft(String draftId) {
        ScriptDraftDO draft = scriptDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new ClientException("草稿不存在。");
        }
        ScriptProjectDO project = scriptProjectMapper.selectOne(new LambdaQueryWrapper<ScriptProjectDO>()
                .eq(ScriptProjectDO::getId, draft.getProjectId())
                .eq(ScriptProjectDO::getCreatedBy, UserContext.requireUser().userId()));
        if (project == null) {
            throw new ClientException("项目不存在或无权访问");
        }
        return draft;
    }

    private String dumpYaml(Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setIndicatorIndent(2);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(value).strip();
    }

    private String optionalText(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record StoryboardAiResult(
            List<ScriptStoryboardResponse.ShotSuggestion> shots,
            List<String> suggestions
    ) {
    }
}
