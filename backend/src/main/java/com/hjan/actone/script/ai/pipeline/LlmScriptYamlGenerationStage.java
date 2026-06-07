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
    private static final Duration DEFAULT_YAML_TIMEOUT = Duration.ofSeconds(120);
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final Pattern LEADING_CODE_FENCE =
            Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE =
            Pattern.compile("\\n?```[\\t ]*$");
    private static final String SCHEMA_SUMMARY = """
            顶层必须包含 schema_version、kind、metadata、source、characters、acts、scenes。
            schema_version 固定为 "1.0"，kind 固定为 "screenplay_draft"。
            source 必须包含 type（固定 "novel"）、generation_mode（固定 "ai_pipeline"）、chapter_count（正整数，与项目章节数一致）。
            acts 每项必须包含 id、title、chapter_range（对象，含 start 和 end 正整数，start <= end）。
            scenes 每项必须包含 id、act_id、chapter_index、source_chapter_id、title、location、time、scene_purpose、action、dialogues、source_excerpt、ai_expansion。
            act_id 必须引用 acts 中已定义的 id；chapter_index 不能超过 source.chapter_count。
            dialogues 每项应包含 speaker、line，可包含 emotion。
            characters 需要从 chapter_summaries 与 scene_plan 中归纳人物名或稳定人物标识。
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
                ? generateBatch(
                context,
                context.chapterSummaries(),
                context.scenePlan(),
                "完整剧本"
        )
                : generateBatches(context);
        log.info("完成YAML生成 taskId={} 耗时={}ms YAML长度={}",
                context.taskId(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                yaml.length());
        return yaml;
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
        merged.put("acts", List.of(defaultAct(context)));
        merged.put("scenes", mergeScenes(documents));
        return merged;
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

    private Map<String, Object> defaultAct(PipelineContext context) {
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("id", "act_1");
        act.put("title", "第一幕");
        act.put("chapter_range", Map.of("start", 1, "end", context.chapterCount()));
        act.put("dramatic_function", "完成小说改编剧本主线");
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
                            "role", "人物",
                            "description", "来自章节摘要的人物。"
                    )));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> character : byName.values()) {
            Map<String, Object> copy = new LinkedHashMap<>(character);
            copy.put("id", "character_%03d".formatted(index++));
            copy.putIfAbsent("name", copy.get("id"));
            result.add(copy);
        }
        return result;
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
                    copy.put("act_id", "act_1");
                    scenes.add(copy);
                }
            }
        }
        return scenes;
    }

    private String dumpYaml(Map<String, Object> document) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
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
