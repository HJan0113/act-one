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
import com.hjan.actone.script.ai.model.SourceEvidence;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class LlmScriptYamlGenerationStage implements ScriptYamlGenerationStage {

    private static final double YAML_TEMPERATURE = 0.15D;
    private static final double YAML_TOP_P = 0.8D;
    private static final int YAML_MAX_TOKENS = 4096;
    private static final int SCENE_BATCH_SIZE = 6;
    private static final Duration DEFAULT_YAML_TIMEOUT = Duration.ofSeconds(240);
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final Pattern LEADING_CODE_FENCE =
            Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE =
            Pattern.compile("\\n?```[\\t ]*$");
    private static final String SCHEMA_SUMMARY = """
            顶层必须包含 schema_version、kind、metadata、source、characters、acts、scenes。
            schema_version 固定为 "1.0"，kind 固定为 "screenplay_draft"。
            source 必须包含 type（固定 "novel"）、generation_mode（固定 "ai_pipeline"）、chapter_count（正整数，与项目章节数一致）。
            acts 每项必须包含 id、title、chapter_range（对象，含 start 和 end 正整数，start <= end），完整剧本需连续覆盖 chapter 1 到 chapter_count。
            scenes 每项必须包含 id、act_id、chapter_index、source_chapter_id、title、location、time、scene_purpose、action、dialogues、source_excerpt、ai_expansion。
            act_id 必须引用 acts 中已定义的 id；chapter_index 不能超过 source.chapter_count。
            dialogues 每项应包含 speaker、line，可包含 emotion。
            characters 需要从 chapter_summaries 与 scene_plan 中归纳人物名或稳定人物标识，并包含 id、name、role、description、aliases、goal、motivation、first_appearance_chapter_id、source_chapter_ids。
            action 需要把心理、情绪和抽象叙述转译为可拍动作、表情、道具、沉默或转场。
            dialogues 需要基于场景冲突生成可编辑对白，避免角色口吻趋同。
            ai_expansion 为 boolean；当 action 或 dialogues 含源文摘录外的影视化、压缩、转场或对白改写时必须为 true。
            """;

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final Executor yamlExecutor;

    public LlmScriptYamlGenerationStage(LLMService llmService,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, DEFAULT_YAML_TIMEOUT, DIRECT_EXECUTOR);
    }

    public LlmScriptYamlGenerationStage(LLMService llmService,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper,
                                        Duration timeout) {
        this(llmService, promptTemplateService, objectMapper, timeout, DIRECT_EXECUTOR);
    }

    public LlmScriptYamlGenerationStage(LLMService llmService,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper,
                                        Duration timeout,
                                        Executor yamlExecutor) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_YAML_TIMEOUT, "YAML 生成 timeout");
        this.yamlExecutor = Objects.requireNonNull(yamlExecutor, "yamlExecutor 不能为空");
    }

    @Override
    public String generate(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.chapterSummaries().isEmpty()) {
            throw new IllegalArgumentException("chapterSummaries 不能为空");
        }
        if (context.scenePlan() == null || context.scenePlan().scenes().isEmpty()) {
            throw new IllegalArgumentException("scenePlan 不能为空");
        }

        long startedNanos = System.nanoTime();
        log.info("开始YAML生成 taskId={} 场景数={}", context.taskId(), context.scenePlan().scenes().size());
        String yaml = context.scenePlan().scenes().size() <= SCENE_BATCH_SIZE
                ? generateSingleBatch(context)
                : generateBatches(context);
        log.info("完成YAML生成 taskId={} 耗时={}ms YAML长度={}",
                context.taskId(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                yaml.length());
        return yaml;
    }

    private String generateSingleBatch(PipelineContext context) {
        String yaml = generateBatch(
                context,
                context.chapterSummaries(),
                context.scenePlan(),
                "完整剧本"
        );
        return dumpYaml(normalizeDocument(context, parseYamlDocument(yaml), context.scenePlan()));
    }

    private String generateBatches(PipelineContext context) {
        List<List<ScenePlan.Scene>> sceneBatches = chunks(context.scenePlan().scenes(), SCENE_BATCH_SIZE);
        List<CompletableFuture<String>> tasks = new ArrayList<>(sceneBatches.size());
        for (int index = 0; index < sceneBatches.size(); index++) {
            List<ScenePlan.Scene> scenes = sceneBatches.get(index);
            List<ChapterSummary> summaries = summariesForScenes(context.chapterSummaries(), scenes);
            ScenePlan batchPlan = new ScenePlan(scenes);
            String scope = "第 %d/%d 批，只生成本批 scene_plan 中的 scenes".formatted(index + 1, sceneBatches.size());
            tasks.add(CompletableFuture.supplyAsync(
                    () -> generateBatch(context, summaries, batchPlan, scope),
                    yamlExecutor
            ));
        }
        joinAll(tasks);
        List<Map<String, Object>> documents = tasks.stream()
                .map(CompletableFuture::join)
                .map(this::parseYamlDocument)
                .toList();
        return dumpYaml(mergeDocuments(context, documents));
    }

    private String generateBatch(PipelineContext context,
                                 List<ChapterSummary> summaries,
                                 ScenePlan scenePlan,
                                 String generationScope) {
        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.SCRIPT_YAML_GENERATION,
                Map.of(
                        "project_title", context.projectTitle(),
                        "author", optionalValue(context.author()),
                        "logline", optionalValue(context.logline()),
                        "chapter_count", context.chapterCount(),
                        "generation_scope", generationScope,
                        "chapter_summaries", serialize(compactSummaries(summaries), "章节摘要序列化失败"),
                        "scene_plan", serialize(compactScenePlan(scenePlan), "场景规划序列化失败"),
                        "schema_summary", SCHEMA_SUMMARY
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                YAML_TEMPERATURE,
                YAML_TOP_P,
                YAML_MAX_TOKENS,
                timeout,
                ChatResponseFormat.TEXT
        ));
        return cleanYaml(response);
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private List<Map<String, Object>> compactSummaries(List<ChapterSummary> summaries) {
        return summaries.stream()
                .map(this::compactSummary)
                .toList();
    }

    private Map<String, Object> compactSummary(ChapterSummary summary) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chapterId", summary.chapterId());
        value.put("chapterIndex", summary.chapterIndex());
        value.put("title", summary.title());
        value.put("summary", summary.summary());
        value.put("events", summary.keyEvents().stream()
                .limit(3)
                .map(event -> Map.of(
                        "description", event.description(),
                        "excerpt", event.evidence().get(0).excerpt()
                ))
                .toList());
        value.put("characters", limit(summary.characters(), 8));
        value.put("locations", limit(summary.locations(), 5));
        value.put("time", limit(summary.timeExpressions(), 3));
        value.put("conflicts", limit(summary.conflicts(), 3));
        value.put("innerStates", summary.innerStates().stream()
                .limit(2)
                .map(innerState -> Map.of(
                        "character", innerState.character(),
                        "description", innerState.description(),
                        "excerpt", innerState.evidence().excerpt()
                ))
                .toList());
        value.put("evidence", summary.sourceEvidence().stream()
                .limit(2)
                .map(SourceEvidence::excerpt)
                .toList());
        return value;
    }

    private Map<String, Object> compactScenePlan(ScenePlan scenePlan) {
        return Map.of("scenes", scenePlan.scenes().stream()
                .map(this::compactScene)
                .toList());
    }

    private Map<String, Object> compactScene(ScenePlan.Scene scene) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", scene.id());
        value.put("actId", scene.actId());
        value.put("chapterIndex", scene.chapterIndex());
        value.put("sourceChapterId", scene.sourceChapterId());
        value.put("title", scene.title());
        value.put("location", scene.location());
        value.put("time", scene.time());
        value.put("characters", limit(scene.characters(), 8));
        value.put("scenePurpose", scene.scenePurpose());
        value.put("conflict", scene.conflict());
        value.put("beats", scene.beats().stream()
                .limit(3)
                .map(beat -> Map.of(
                        "description", beat.description(),
                        "sourceExcerpt", beat.sourceExcerpt(),
                        "characterIds", limit(beat.characterIds(), 8)
                ))
                .toList());
        value.put("sourceExcerpt", scene.sourceExcerpt());
        return value;
    }

    private List<String> limit(List<String> values, int limit) {
        return values.stream().limit(limit).toList();
    }

    private String cleanYaml(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 剧本 YAML 返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        String cleaned = response.content()
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.strip();
        if (!hasText(cleaned)) {
            throw new RemoteException("AI 剧本 YAML 返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        return cleaned;
    }

    private String optionalValue(String value) {
        return hasText(value) ? value : "";
    }

    private List<ChapterSummary> summariesForScenes(List<ChapterSummary> summaries, List<ScenePlan.Scene> scenes) {
        Set<String> chapterIds = new LinkedHashSet<>();
        scenes.forEach(scene -> chapterIds.add(scene.sourceChapterId()));
        List<ChapterSummary> filtered = summaries.stream()
                .filter(summary -> chapterIds.contains(summary.chapterId()))
                .toList();
        return filtered.isEmpty() ? summaries : filtered;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlDocument(String yaml) {
        Object parsed = newYamlParser().load(yaml);
        if (!(parsed instanceof Map<?, ?> document)) {
            throw new RemoteException("AI 剧本 YAML 顶层结构非法", BaseErrorCode.REMOTE_ERROR);
        }
        return (Map<String, Object>) document;
    }

    private Map<String, Object> mergeDocuments(PipelineContext context, List<Map<String, Object>> documents) {
        Map<String, Object> first = documents.get(0);
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("schema_version", "1.0");
        merged.put("kind", "screenplay_draft");
        merged.put("metadata", first.getOrDefault("metadata", defaultMetadata(context)));
        merged.put("source", defaultSource(context));
        merged.put("characters", mergeCharacters(context, documents));
        merged.put("acts", defaultActs(context));
        merged.put("scenes", mergeScenes(documents));
        return normalizeDocument(context, merged, context.scenePlan());
    }

    private Map<String, Object> normalizeDocument(PipelineContext context,
                                                  Map<String, Object> document,
                                                  ScenePlan scenePlan) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("schema_version", "1.0");
        normalized.put("kind", "screenplay_draft");
        normalized.put("metadata", normalizeMetadata(context, document.get("metadata")));
        normalized.put("source", defaultSource(context));
        normalized.put("characters", mergeCharacters(context, List.of(document)));
        normalized.put("acts", defaultActs(context));
        normalized.put("scenes", normalizeScenes(document.get("scenes"), scenePlan, context.chapterCount()));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeMetadata(PipelineContext context, Object value) {
        Map<String, Object> metadata = value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : defaultMetadata(context);
        if (!hasText(text(metadata.get("title")))) {
            metadata.put("title", context.projectTitle());
        }
        if (!hasText(text(metadata.get("author"))) && hasText(context.author())) {
            metadata.put("author", context.author());
        }
        if (!hasText(text(metadata.get("logline"))) && hasText(context.logline())) {
            metadata.put("logline", context.logline());
        }
        return metadata;
    }

    private Map<String, Object> defaultMetadata(PipelineContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", context.projectTitle());
        if (hasText(context.author())) {
            metadata.put("author", context.author());
        }
        if (hasText(context.logline())) {
            metadata.put("logline", context.logline());
        }
        return metadata;
    }

    private Map<String, Object> defaultSource(PipelineContext context) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "novel");
        source.put("chapter_count", context.chapterCount());
        source.put("generation_mode", "ai_pipeline");
        return source;
    }

    private List<Map<String, Object>> defaultActs(PipelineContext context) {
        int chapterCount = Math.max(1, context.chapterCount());
        if (chapterCount < 3) {
            return List.of(defaultAct("act_1", "第一幕", "完成小说改编剧本主线", 1, chapterCount));
        }
        int firstEnd = Math.max(1, chapterCount / 3);
        int secondEnd = Math.max(firstEnd + 1, chapterCount * 2 / 3);
        return List.of(
                defaultAct("act_1", "第一幕", "建立人物、目标与初始冲突", 1, firstEnd),
                defaultAct("act_2", "第二幕", "推进阻碍、选择与关系变化", firstEnd + 1, secondEnd),
                defaultAct("act_3", "第三幕", "完成高潮、抉择与结局", secondEnd + 1, chapterCount)
        );
    }

    private Map<String, Object> defaultAct(String id, String title, String dramaticFunction, int start, int end) {
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("id", id);
        act.put("title", title);
        act.put("chapter_range", Map.of("start", start, "end", end));
        act.put("dramatic_function", dramaticFunction);
        return act;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mergeCharacters(PipelineContext context, List<Map<String, Object>> documents) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> document : documents) {
            Object value = document.get("characters");
            if (!(value instanceof List<?> characters)) {
                continue;
            }
            for (Object item : characters) {
                if (item instanceof Map<?, ?> character) {
                    Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) character);
                    String name = text(copy.get("name"));
                    String key = hasText(name) ? name : text(copy.get("id"));
                    if (hasText(key)) {
                        byName.putIfAbsent(key, copy);
                    }
                }
            }
        }
        if (byName.isEmpty()) {
            context.chapterSummaries().stream()
                    .flatMap(summary -> summary.characters().stream())
                    .distinct()
                    .forEach(name -> byName.put(name, Map.of(
                            "name", name,
                            "role", "主要人物",
                            "description", "来自章节摘要的人物。"
                    )));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> character : byName.values()) {
            result.add(normalizeCharacter(context, character, index++));
        }
        return result;
    }

    private Map<String, Object> normalizeCharacter(PipelineContext context, Map<String, Object> character, int index) {
        Map<String, Object> normalized = new LinkedHashMap<>(character);
        String name = firstText(normalized.get("name"), normalized.get("id"), "人物%03d".formatted(index));
        normalized.put("id", firstText(normalized.get("id"), "character_%03d".formatted(index)));
        normalized.put("name", name);
        normalized.put("role", firstText(normalized.get("role"), "主要人物"));
        normalized.put("description", firstText(normalized.get("description"), "根据章节摘要归纳的剧中人物。"));
        normalized.put("aliases", stringList(normalized.get("aliases")));
        normalized.put("goal", firstText(normalized.get("goal"), normalized.get("objective"), "推进当前剧情目标。"));
        normalized.put("motivation", firstText(normalized.get("motivation"), "围绕核心冲突采取行动。"));
        normalized.put(
                "first_appearance_chapter_id",
                firstText(normalized.get("first_appearance_chapter_id"), firstChapterIdForCharacter(context, name))
        );
        List<String> sourceChapterIds = stringList(normalized.get("source_chapter_ids"));
        if (sourceChapterIds.isEmpty()) {
            sourceChapterIds = stringList(normalized.get("sourceChapterIds"));
        }
        if (sourceChapterIds.isEmpty()) {
            sourceChapterIds = sourceChapterIdsForCharacter(context, name);
        }
        normalized.put("source_chapter_ids", sourceChapterIds);
        return normalized;
    }

    private String firstChapterIdForCharacter(PipelineContext context, String characterName) {
        return sourceChapterIdsForCharacter(context, characterName).stream()
                .findFirst()
                .orElse("chapter_1");
    }

    private List<String> sourceChapterIdsForCharacter(PipelineContext context, String characterName) {
        List<String> sourceChapterIds = context.chapterSummaries().stream()
                .filter(summary -> summary.characters().stream().anyMatch(name -> name.equals(characterName)))
                .map(ChapterSummary::chapterId)
                .distinct()
                .toList();
        if (!sourceChapterIds.isEmpty()) {
            return sourceChapterIds;
        }
        return context.chapterSummaries().stream()
                .map(ChapterSummary::chapterId)
                .findFirst()
                .map(List::of)
                .orElse(List.of("chapter_1"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mergeScenes(List<Map<String, Object>> documents) {
        List<Map<String, Object>> scenes = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            Object value = document.get("scenes");
            if (!(value instanceof List<?> batchScenes)) {
                continue;
            }
            for (Object item : batchScenes) {
                if (item instanceof Map<?, ?> scene) {
                    Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) scene);
                    copy.put("id", "scene_%03d".formatted(scenes.size() + 1));
                    scenes.add(copy);
                }
            }
        }
        return scenes;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeScenes(Object value, ScenePlan scenePlan, int chapterCount) {
        List<?> rawScenes = value instanceof List<?> list ? list : List.of();
        int size = Math.max(rawScenes.size(), scenePlan.scenes().size());
        List<Map<String, Object>> scenes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Map<String, Object> raw = index < rawScenes.size() && rawScenes.get(index) instanceof Map<?, ?> scene
                    ? new LinkedHashMap<>((Map<String, Object>) scene)
                    : new LinkedHashMap<>();
            ScenePlan.Scene fallback = findScene(scenePlan, text(raw.get("id")), index);
            scenes.add(normalizeScene(raw, fallback, scenes.size() + 1, chapterCount));
        }
        return scenes;
    }

    private Map<String, Object> normalizeScene(Map<String, Object> raw,
                                               ScenePlan.Scene fallback,
                                               int sceneIndex,
                                               int chapterCount) {
        Map<String, Object> scene = new LinkedHashMap<>();
        int chapterIndex = positiveInt(raw.get("chapter_index"));
        if (chapterIndex <= 0) {
            chapterIndex = positiveInt(raw.get("chapterIndex"));
        }
        if (chapterIndex <= 0 && fallback != null) {
            chapterIndex = fallback.chapterIndex();
        }
        chapterIndex = Math.max(1, Math.min(Math.max(1, chapterCount), chapterIndex <= 0 ? 1 : chapterIndex));

        scene.put("id", "scene_%03d".formatted(sceneIndex));
        scene.put("act_id", actIdForChapter(chapterIndex, chapterCount));
        scene.put("chapter_index", chapterIndex);
        scene.put("source_chapter_id", firstText(
                raw.get("source_chapter_id"),
                raw.get("sourceChapterId"),
                fallback == null ? null : fallback.sourceChapterId(),
                "chapter_" + chapterIndex
        ));
        scene.put("title", firstText(raw.get("title"), fallback == null ? null : fallback.title(), "第 " + sceneIndex + " 场"));
        scene.put("location", firstText(raw.get("location"), fallback == null ? null : fallback.location(), "待定地点"));
        scene.put("time", firstText(raw.get("time"), fallback == null ? null : fallback.time(), "待定时间"));
        scene.put("scene_purpose", firstText(
                raw.get("scene_purpose"),
                raw.get("scenePurpose"),
                fallback == null ? null : fallback.scenePurpose(),
                "推进剧情。"
        ));
        scene.put("action", firstText(raw.get("action"), fallbackAction(fallback), "人物在场景中完成关键行动。"));
        scene.put("dialogues", normalizeDialogues(raw.get("dialogues")));
        scene.put("source_excerpt", firstText(
                raw.get("source_excerpt"),
                raw.get("sourceExcerpt"),
                fallback == null ? null : fallback.sourceExcerpt(),
                fallbackAction(fallback),
                "来源摘录待补充。"
        ));
        scene.put("ai_expansion", raw.get("ai_expansion") instanceof Boolean value ? value : Boolean.TRUE);
        Object characters = raw.get("characters");
        if (characters instanceof List<?> list) {
            scene.put("characters", list);
        } else if (fallback != null && !fallback.characters().isEmpty()) {
            scene.put("characters", fallback.characters());
        }
        return scene;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeDialogues(Object value) {
        if (!(value instanceof List<?> rawDialogues)) {
            return List.of();
        }
        List<Map<String, Object>> dialogues = new ArrayList<>();
        for (Object item : rawDialogues) {
            if (!(item instanceof Map<?, ?> dialogue)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) dialogue);
            String speaker = firstText(copy.get("speaker"), "旁白");
            String line = text(copy.get("line"));
            if (!hasText(line)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("speaker", speaker);
            normalized.put("line", line);
            if (hasText(text(copy.get("emotion")))) {
                normalized.put("emotion", text(copy.get("emotion")));
            }
            dialogues.add(normalized);
        }
        return dialogues;
    }

    private String actIdForChapter(int chapterIndex, int chapterCount) {
        if (chapterCount < 3) {
            return "act_1";
        }
        int firstEnd = Math.max(1, chapterCount / 3);
        int secondEnd = Math.max(firstEnd + 1, chapterCount * 2 / 3);
        if (chapterIndex <= firstEnd) {
            return "act_1";
        }
        if (chapterIndex <= secondEnd) {
            return "act_2";
        }
        return "act_3";
    }

    private ScenePlan.Scene findScene(ScenePlan scenePlan, String sceneId, int index) {
        if (hasText(sceneId)) {
            for (ScenePlan.Scene scene : scenePlan.scenes()) {
                if (sceneId.equals(scene.id())) {
                    return scene;
                }
            }
        }
        return index >= 0 && index < scenePlan.scenes().size() ? scenePlan.scenes().get(index) : null;
    }

    private String fallbackAction(ScenePlan.Scene scene) {
        if (scene == null || scene.beats().isEmpty()) {
            return "";
        }
        return scene.beats().stream()
                .map(ScenePlan.Beat::description)
                .filter(this::hasText)
                .findFirst()
                .orElse("");
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private int positiveInt(Object value) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return 0;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> rawValues)) {
            return List.of();
        }
        return rawValues.stream()
                .map(this::text)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String dumpYaml(Map<String, Object> document) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setIndicatorIndent(2);
        options.setPrettyFlow(false);
        return new Yaml(options).dump(document).strip();
    }

    private Yaml newYamlParser() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private <T> List<List<T>> chunks(List<T> values, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            result.add(values.subList(start, Math.min(start + size, values.size())));
        }
        return result;
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

    private String text(Object value) {
        return value instanceof String text ? text.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
