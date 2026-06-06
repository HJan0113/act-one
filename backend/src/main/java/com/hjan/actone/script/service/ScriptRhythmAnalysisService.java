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
import com.hjan.actone.script.model.ScriptRhythmAnalysisResponse;
import com.hjan.actone.script.model.ScriptRhythmOverallMetrics;
import com.hjan.actone.script.model.ScriptSceneRhythmMetric;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ScriptRhythmAnalysisService {

    private static final double RHYTHM_TEMPERATURE = 0.1D;
    private static final double RHYTHM_TOP_P = 0.7D;
    private static final int RHYTHM_MAX_TOKENS = 1536;
    private static final Duration RHYTHM_TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```[\\t ]*$");
    private static final Set<String> CONFLICT_KEYWORDS = Set.of(
            "冲突", "对峙", "争执", "威胁", "逼", "追", "逃", "危险", "杀", "死", "揭穿", "秘密", "阻止", "选择"
    );
    private static final Set<String> TURNING_KEYWORDS = Set.of(
            "发现", "突然", "原来", "却", "但是", "决定", "转身", "揭开", "暴露", "改变", "反转", "意识到"
    );
    private static final Set<String> PURPOSE_KEYWORDS = Set.of("建立", "推进", "揭示", "转折", "冲突", "铺垫", "引出", "完成");

    private static final String AI_ASSESSMENT_SCHEMA = """
            返回 JSON_OBJECT：
            {
              "sceneAssessments": [
                {
                  "sceneId": "scene_001",
                  "conflictScore": 1-5,
                  "turningScore": 1-5,
                  "purposeScore": 1-5,
                  "summary": "一句节奏判断",
                  "warnings": ["可选风险"]
                }
              ],
              "suggestions": ["整体节奏建议"]
            }
            评分必须基于 scenes_metrics_json，不得新增剧情事实。
            """;

    private final ScriptDraftMapper scriptDraftMapper;
    private final ScriptProjectMapper scriptProjectMapper;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public ScriptRhythmAnalysisService(ScriptDraftMapper scriptDraftMapper,
                                       ScriptProjectMapper scriptProjectMapper,
                                       ObjectProvider<LLMService> llmServiceProvider,
                                       PromptTemplateService promptTemplateService,
                                       ObjectMapper objectMapper) {
        this.scriptDraftMapper = Objects.requireNonNull(scriptDraftMapper, "ScriptDraftMapper 不能为空");
        this.scriptProjectMapper = Objects.requireNonNull(scriptProjectMapper, "ScriptProjectMapper 不能为空");
        this.llmServiceProvider = Objects.requireNonNull(llmServiceProvider, "LLMService ObjectProvider 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    public ScriptRhythmAnalysisResponse analyze(String draftId) {
        String normalizedDraftId = requireText(draftId, "草稿 ID 不能为空。");
        ScriptDraftDO draft = loadOwnedDraft(normalizedDraftId);
        List<ScriptSceneRhythmMetric> ruleMetrics = calculateRuleMetrics(parseScenes(draft.getYamlContent()));
        RhythmAssessmentResult assessment = assessWithAi(ruleMetrics);
        return new ScriptRhythmAnalysisResponse(
                draft.getId(),
                assessment.mode(),
                calculateOverall(assessment.scenes()),
                assessment.scenes(),
                assessment.suggestions()
        );
    }

    private RhythmAssessmentResult assessWithAi(List<ScriptSceneRhythmMetric> ruleMetrics) {
        List<String> ruleSuggestions = createOverallSuggestions(ruleMetrics);
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null || ruleMetrics.isEmpty()) {
            return new RhythmAssessmentResult("rule_based_fallback", ruleMetrics, ruleSuggestions);
        }

        try {
            RenderedPrompt prompt = promptTemplateService.render(
                    PromptTemplateId.RHYTHM_ANALYSIS,
                    Map.of(
                            "scenes_metrics_json", metricsJson(ruleMetrics),
                            "schema_summary", AI_ASSESSMENT_SCHEMA
                    )
            );
            ChatResponse response = llmService.chat(new ChatRequest(
                    List.of(ChatMessage.user(prompt.content())),
                    null,
                    RHYTHM_TEMPERATURE,
                    RHYTHM_TOP_P,
                    RHYTHM_MAX_TOKENS,
                    RHYTHM_TIMEOUT,
                    ChatResponseFormat.JSON_OBJECT
            ));
            RhythmAiResult aiResult = parseAiResult(response);
            return new RhythmAssessmentResult(
                    "ai_assisted",
                    mergeAiAssessments(ruleMetrics, aiResult.sceneAssessments()),
                    mergeSuggestions(ruleSuggestions, aiResult.suggestions())
            );
        } catch (RuntimeException ex) {
            List<String> fallbackSuggestions = new ArrayList<>(ruleSuggestions);
            fallbackSuggestions.add("AI 节奏评估不可用，当前结果为规则指标兜底。");
            return new RhythmAssessmentResult("rule_based_fallback", ruleMetrics, fallbackSuggestions);
        }
    }

    private List<ScriptSceneRhythmMetric> calculateRuleMetrics(List<Map<String, Object>> scenes) {
        List<ScriptSceneRhythmMetric> metrics = new ArrayList<>();
        for (Object value : scenes) {
            if (!(value instanceof Map<?, ?> rawScene)) {
                continue;
            }
            Map<?, ?> scene = rawScene;
            String sceneId = optionalText(scene, "id");
            String title = optionalText(scene, "title");
            String action = optionalText(scene, "action");
            String purpose = optionalText(scene, "scene_purpose");
            List<String> dialogueLines = readDialogueLines(scene);
            int actionChars = countReadableCharacters(action);
            int dialogueChars = dialogueLines.stream().mapToInt(this::countReadableCharacters).sum();
            int totalChars = actionChars + dialogueChars;
            int actionRatio = percentage(actionChars, totalChars);
            int dialogueRatio = percentage(dialogueChars, totalChars);
            int conflictScore = scoreConflict(title + "\n" + purpose + "\n" + action, dialogueLines.size(), actionChars);
            int turningScore = scoreTurning(purpose + "\n" + action + "\n" + String.join("\n", dialogueLines));
            int purposeScore = scorePurpose(purpose);
            String paceLevel = paceLevel(actionRatio, dialogueRatio, conflictScore, turningScore, purposeScore);
            List<String> warnings = sceneWarnings(dialogueLines.size(), actionRatio, dialogueRatio, conflictScore, turningScore, purposeScore);
            metrics.add(new ScriptSceneRhythmMetric(
                    sceneId,
                    title,
                    actionChars,
                    dialogueChars,
                    dialogueLines.size(),
                    actionRatio,
                    dialogueRatio,
                    conflictScore,
                    turningScore,
                    purposeScore,
                    paceLevel,
                    "对白占比 " + dialogueRatio + "%，冲突 " + conflictScore + "/5，转折 " + turningScore + "/5。",
                    warnings
            ));
        }
        return metrics;
    }

    private ScriptRhythmOverallMetrics calculateOverall(List<ScriptSceneRhythmMetric> scenes) {
        int sceneCount = scenes.size();
        int dialogueCount = scenes.stream().mapToInt(ScriptSceneRhythmMetric::dialogueCount).sum();
        int actionChars = scenes.stream().mapToInt(ScriptSceneRhythmMetric::actionCharCount).sum();
        int dialogueChars = scenes.stream().mapToInt(ScriptSceneRhythmMetric::dialogueCharCount).sum();
        int totalChars = actionChars + dialogueChars;
        int averageConflict = sceneCount == 0 ? 0 : roundedAverage(scenes.stream().mapToInt(ScriptSceneRhythmMetric::conflictScore).sum(), sceneCount);
        int averageTurning = sceneCount == 0 ? 0 : roundedAverage(scenes.stream().mapToInt(ScriptSceneRhythmMetric::turningScore).sum(), sceneCount);
        long clearPurposeScenes = scenes.stream().filter(scene -> scene.purposeScore() >= 3).count();
        long imbalanceScenes = scenes.stream().filter(scene -> !scene.warnings().isEmpty()).count();
        return new ScriptRhythmOverallMetrics(
                sceneCount,
                dialogueCount,
                actionChars,
                dialogueChars,
                percentage(actionChars, totalChars),
                percentage(dialogueChars, totalChars),
                averageConflict,
                averageTurning,
                percentage((int) clearPurposeScenes, sceneCount),
                (int) imbalanceScenes
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseScenes(String yamlContent) {
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlContent);
            if (!(parsed instanceof Map<?, ?> document)) {
                throw new ClientException("YAML 顶层结构必须是对象。");
            }
            Object scenes = document.get("scenes");
            if (!(scenes instanceof List<?> sceneList)) {
                throw new ClientException("YAML scenes 必须是数组。");
            }
            return (List<Map<String, Object>>) scenes;
        } catch (YAMLException ex) {
            throw new ClientException("草稿 YAML 无法解析，不能执行节奏分析。", ex, BaseErrorCode.CLIENT_ERROR);
        }
    }

    private List<String> readDialogueLines(Map<?, ?> scene) {
        Object dialogues = scene.get("dialogues");
        if (!(dialogues instanceof List<?> dialogueList)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object value : dialogueList) {
            if (value instanceof Map<?, ?> dialogue) {
                String line = optionalText(dialogue, "line");
                if (StringUtils.hasText(line)) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private int scoreConflict(String text, int dialogueCount, int actionChars) {
        int score = 1 + keywordScore(text, CONFLICT_KEYWORDS, 3);
        if (dialogueCount >= 2) {
            score++;
        }
        if (actionChars >= 120) {
            score++;
        }
        return clampScore(score);
    }

    private int scoreTurning(String text) {
        return clampScore(1 + keywordScore(text, TURNING_KEYWORDS, 4));
    }

    private int scorePurpose(String purpose) {
        if (!StringUtils.hasText(purpose) || purpose.contains("待补充")) {
            return 1;
        }
        int score = purpose.trim().length() >= 8 ? 3 : 2;
        score += keywordScore(purpose, PURPOSE_KEYWORDS, 2);
        return clampScore(score);
    }

    private int keywordScore(String text, Set<String> keywords, int limit) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int score = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score++;
            }
        }
        return Math.min(score, limit);
    }

    private List<String> sceneWarnings(int dialogueCount,
                                       int actionRatio,
                                       int dialogueRatio,
                                       int conflictScore,
                                       int turningScore,
                                       int purposeScore) {
        List<String> warnings = new ArrayList<>();
        if (dialogueRatio >= 75) {
            warnings.add("对白占比偏高，可能缺少可拍动作或场面推进。");
        }
        if (actionRatio >= 88 && dialogueCount == 0) {
            warnings.add("动作占比偏高且无对白，可能需要补表演或声音信息。");
        }
        if (conflictScore <= 2) {
            warnings.add("冲突强度偏低，场景可能显得平。");
        }
        if (turningScore <= 2) {
            warnings.add("转折信号偏弱，建议明确发现、选择或反转。");
        }
        if (purposeScore <= 2) {
            warnings.add("场景目的不清，建议补充 scene_purpose。");
        }
        return warnings;
    }

    private String paceLevel(int actionRatio, int dialogueRatio, int conflictScore, int turningScore, int purposeScore) {
        if (purposeScore <= 2) {
            return "目的不清";
        }
        if (conflictScore <= 2) {
            return "冲突不足";
        }
        if (turningScore <= 2) {
            return "转折偏弱";
        }
        if (dialogueRatio >= 75) {
            return "对白偏重";
        }
        if (actionRatio >= 88) {
            return "动作偏重";
        }
        return "均衡";
    }

    private List<ScriptSceneRhythmMetric> mergeAiAssessments(List<ScriptSceneRhythmMetric> ruleMetrics,
                                                             List<RhythmAiSceneAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return ruleMetrics;
        }
        Map<String, RhythmAiSceneAssessment> aiByScene = new LinkedHashMap<>();
        for (RhythmAiSceneAssessment assessment : assessments) {
            if (StringUtils.hasText(assessment.sceneId())) {
                aiByScene.put(assessment.sceneId().trim(), assessment);
            }
        }
        return ruleMetrics.stream()
                .map(metric -> mergeMetric(metric, aiByScene.get(metric.sceneId())))
                .toList();
    }

    private ScriptSceneRhythmMetric mergeMetric(ScriptSceneRhythmMetric metric, RhythmAiSceneAssessment assessment) {
        if (assessment == null) {
            return metric;
        }
        int conflictScore = scoreOrDefault(assessment.conflictScore(), metric.conflictScore());
        int turningScore = scoreOrDefault(assessment.turningScore(), metric.turningScore());
        int purposeScore = scoreOrDefault(assessment.purposeScore(), metric.purposeScore());
        List<String> warnings = mergeSuggestions(metric.warnings(), assessment.warnings());
        return new ScriptSceneRhythmMetric(
                metric.sceneId(),
                metric.title(),
                metric.actionCharCount(),
                metric.dialogueCharCount(),
                metric.dialogueCount(),
                metric.actionRatio(),
                metric.dialogueRatio(),
                conflictScore,
                turningScore,
                purposeScore,
                paceLevel(metric.actionRatio(), metric.dialogueRatio(), conflictScore, turningScore, purposeScore),
                StringUtils.hasText(assessment.summary()) ? assessment.summary().trim() : metric.summary(),
                warnings
        );
    }

    private List<String> createOverallSuggestions(List<ScriptSceneRhythmMetric> metrics) {
        if (metrics.isEmpty()) {
            return List.of("当前 YAML 没有可分析场景。");
        }
        List<String> suggestions = new ArrayList<>();
        long weakConflict = metrics.stream().filter(metric -> metric.conflictScore() <= 2).count();
        long weakTurning = metrics.stream().filter(metric -> metric.turningScore() <= 2).count();
        long imbalancedRatio = metrics.stream().filter(metric -> metric.dialogueRatio() >= 75 || metric.actionRatio() >= 88).count();
        if (weakConflict > 0) {
            suggestions.add("有 " + weakConflict + " 个场景冲突偏弱，优先补对抗目标或阻碍。");
        }
        if (weakTurning > 0) {
            suggestions.add("有 " + weakTurning + " 个场景转折偏弱，建议明确发现、选择或反转点。");
        }
        if (imbalancedRatio > 0) {
            suggestions.add("有 " + imbalancedRatio + " 个场景动作/对白比例失衡，建议补动作或压缩对白。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前剧本节奏较均衡，可进入局部精修。");
        }
        return suggestions;
    }

    private RhythmAiResult parseAiResult(ChatResponse response) {
        if (response == null || !StringUtils.hasText(response.content())) {
            throw new ClientException("AI 节奏评估返回为空。");
        }
        String json = response.content()
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        json = LEADING_CODE_FENCE.matcher(json).replaceFirst("");
        json = TRAILING_CODE_FENCE.matcher(json).replaceFirst("");
        try {
            RhythmAiResult result = objectMapper.readValue(json, RhythmAiResult.class);
            return result == null ? new RhythmAiResult(List.of(), List.of()) : result;
        } catch (JsonProcessingException ex) {
            throw new ClientException("AI 节奏评估 JSON 解析失败。", ex, BaseErrorCode.CLIENT_ERROR);
        }
    }

    private String metricsJson(List<ScriptSceneRhythmMetric> metrics) {
        List<Map<String, Object>> scenes = new ArrayList<>();
        for (ScriptSceneRhythmMetric metric : metrics) {
            Map<String, Object> scene = new LinkedHashMap<>();
            scene.put("sceneId", metric.sceneId());
            scene.put("title", metric.title());
            scene.put("dialogueRatio", metric.dialogueRatio());
            scene.put("actionRatio", metric.actionRatio());
            scene.put("dialogueCount", metric.dialogueCount());
            scene.put("conflictScore", metric.conflictScore());
            scene.put("turningScore", metric.turningScore());
            scene.put("purposeScore", metric.purposeScore());
            scene.put("paceLevel", metric.paceLevel());
            scene.put("summary", metric.summary());
            scene.put("warnings", metric.warnings());
            scenes.add(scene);
        }
        try {
            return objectMapper.writeValueAsString(scenes);
        } catch (JsonProcessingException ex) {
            throw new ClientException("节奏指标序列化失败。", ex, BaseErrorCode.CLIENT_ERROR);
        }
    }

    private List<String> mergeSuggestions(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(StringUtils::hasText).map(String::trim).forEach(values::add);
        }
        if (second != null) {
            second.stream().filter(StringUtils::hasText).map(String::trim).forEach(values::add);
        }
        return List.copyOf(values);
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

    private int countReadableCharacters(String value) {
        return value == null ? 0 : value.replaceAll("\\s+", "").length();
    }

    private int percentage(int part, int total) {
        return total <= 0 ? 0 : Math.round(part * 100F / total);
    }

    private int roundedAverage(int sum, int count) {
        return count <= 0 ? 0 : Math.round(sum * 1F / count);
    }

    private int scoreOrDefault(Integer value, int fallback) {
        return value == null ? fallback : clampScore(value);
    }

    private int clampScore(int value) {
        return Math.max(1, Math.min(5, value));
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

    private record RhythmAssessmentResult(
            String mode,
            List<ScriptSceneRhythmMetric> scenes,
            List<String> suggestions
    ) {
    }

    private record RhythmAiResult(
            List<RhythmAiSceneAssessment> sceneAssessments,
            List<String> suggestions
    ) {
    }

    private record RhythmAiSceneAssessment(
            String sceneId,
            Integer conflictScore,
            Integer turningScore,
            Integer purposeScore,
            String summary,
            List<String> warnings
    ) {
    }
}
