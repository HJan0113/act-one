package com.hjan.actone.script.ai.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.infra.ai.prompt.ClasspathPromptTemplateService;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAiPipelineReducedChainFakeLlmTest {

    private static final Path NOTICE_NOVEL = resolveNoticeNovel();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    private static Path resolveNoticeNovel() {
        Path rootRelative = Path.of("notice", "测试小说文本.txt");
        if (Files.exists(rootRelative)) {
            return rootRelative;
        }
        return Path.of("..", "notice", "测试小说文本.txt");
    }

    @Test
    void reducedPipelineRunsRealLlmStagesWithFakeLlmService() throws IOException {
        String novelText = Files.readString(NOTICE_NOVEL);
        FakeReducedPipelineLlmService llmService = new FakeReducedPipelineLlmService(objectMapper);
        var promptTemplateService = new ClasspathPromptTemplateService(new DefaultResourceLoader());
        ScriptAiPipelineService pipelineService = new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                new LlmChapterSummaryStage(llmService, promptTemplateService, objectMapper),
                new LlmScenePlanningStage(llmService, promptTemplateService, objectMapper),
                new LlmScriptYamlGenerationStage(llmService, promptTemplateService, objectMapper)
        );
        List<String> events = new ArrayList<>();

        ScriptAiPipelineResult result = pipelineService.generate(
                new ScriptYamlGenerateRequest("星轨回音", "测试作者", "记忆打捞员卷入城市级阴谋。", novelText),
                new ScriptAiPipelineObserver() {
                    @Override
                    public void onStageStart(PipelineContext context, PipelineStage stage) {
                        events.add("start:" + stage.name());
                    }

                    @Override
                    public void onStageSuccess(PipelineContext context, PipelineStage stage) {
                        events.add("success:" + stage.name());
                    }
                }
        );

        assertThat(events).containsSubsequence(
                "start:CHAPTER_SUMMARY",
                "success:CHAPTER_SUMMARY",
                "start:SCENE_PLANNING",
                "success:SCENE_PLANNING",
                "start:SCRIPT_YAML_GENERATION",
                "success:SCRIPT_YAML_GENERATION",
                "start:YAML_VALIDATION",
                "success:YAML_VALIDATION"
        );
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.AI_PIPELINE);
        assertThat(result.fallback()).isFalse();
        assertThat(result.validationResult().status()).isEqualTo(ScriptYamlValidator.VALIDATION_STATUS_VALID);
        assertThat(result.yaml()).isNotBlank();
        assertThat(result.yaml())
                .contains("schema_version")
                .contains("kind")
                .contains("source:")
                .contains("characters:")
                .contains("acts:")
                .contains("scenes:");

        Map<String, Object> document = parseYaml(result.yaml());
        assertThat(document).containsKeys("schema_version", "kind", "source", "characters", "acts", "scenes");
        assertThat(asList(document.get("scenes"))).isNotEmpty();
        int scenePlanCalls = (result.chapterCount() + 2) / 3;
        int yamlCalls = (asList(document.get("scenes")).size() + 5) / 6;
        assertThat(llmService.callCount()).isEqualTo(result.chapterCount() + scenePlanCalls + yamlCalls);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yaml) {
        Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    private static final class FakeReducedPipelineLlmService implements LLMService {

        private static final Pattern CHAPTER_ID = Pattern.compile("(?m)^- chapter_id: (.+)$");
        private static final Pattern CHAPTER_INDEX = Pattern.compile("(?m)^- chapter_index: (\\d+)$");
        private static final Pattern CHAPTER_TITLE = Pattern.compile("(?m)^- chapter_title: (.+)$");

        private final ObjectMapper objectMapper;
        private int callCount;

        private FakeReducedPipelineLlmService(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            callCount++;
            String prompt = request.messages().get(0).content();
            try {
                if (prompt.contains("<scene_plan>")) {
                    return ChatResponse.text(yamlResponse(prompt));
                }
                if (prompt.contains("<chapter_summaries>")) {
                    return ChatResponse.text(scenePlanResponse(prompt));
                }
                return ChatResponse.text(chapterSummaryResponse(prompt));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Fake LLM 响应序列化失败", ex);
            }
        }

        int callCount() {
            return callCount;
        }

        private String chapterSummaryResponse(String prompt) throws JsonProcessingException {
            String chapterId = extract(CHAPTER_ID, prompt);
            int chapterIndex = Integer.parseInt(extract(CHAPTER_INDEX, prompt));
            String title = extract(CHAPTER_TITLE, prompt);
            String content = between(prompt, "<chapter_content>", "</chapter_content>").strip();
            String excerpt = excerpt(content);
            SourceEvidence evidence = new SourceEvidence(chapterId, chapterIndex, excerpt);
            ChapterSummary summary = new ChapterSummary(
                    chapterId,
                    chapterIndex,
                    title,
                    "林溯在新沪市的记忆打捞事件中逐步接近先知组织的阴谋。",
                    List.of(new ChapterSummary.KeyEvent(
                            "event_%03d".formatted(chapterIndex),
                            title + " 的核心事件推动主角接近真相。",
                            List.of(evidence)
                    )),
                    List.of("林溯", "苏雅", "陈默"),
                    List.of("新沪市", "下城区"),
                    List.of("雨夜"),
                    List.of("追查真相与逃避清扫者之间的冲突"),
                    List.of(new ChapterSummary.InnerState("林溯", "他在危险中仍选择追查妹妹失踪的真相。", evidence)),
                    List.of(evidence)
            );
            return objectMapper.writeValueAsString(summary);
        }

        private String scenePlanResponse(String prompt) throws JsonProcessingException {
            List<Map<String, Object>> summaries = objectMapper.readValue(
                    between(prompt, "<chapter_summaries>", "</chapter_summaries>").strip(),
                    new TypeReference<>() {
                    }
            );
            List<ScenePlan.Scene> scenes = new ArrayList<>();
            for (Map<String, Object> summary : summaries) {
                String chapterId = text(summary.get("chapterId"));
                int chapterIndex = ((Number) summary.get("chapterIndex")).intValue();
                List<String> characters = stringList(summary.get("characters"));
                List<String> locations = stringList(summary.get("locations"));
                List<String> times = stringList(summary.get("time"));
                List<String> conflicts = stringList(summary.get("conflicts"));
                String excerpt = firstString(summary.get("evidence"));
                scenes.add(new ScenePlan.Scene(
                        "scene_%03d".formatted(scenes.size() + 1),
                        "act_1",
                        chapterIndex,
                        chapterId,
                        text(summary.get("title")),
                        locations.isEmpty() ? "新沪市" : locations.get(0),
                        times.isEmpty() ? "夜" : times.get(0),
                        characters.isEmpty() ? List.of("林溯") : characters,
                        "把章节核心事件整理为可拍摄场景。",
                        conflicts.isEmpty() ? "主角面对未知危险。" : conflicts.get(0),
                        List.of(new ScenePlan.Beat(
                                text(summary.get("summary")),
                                excerpt,
                                characters.isEmpty() ? List.of("林溯") : characters
                        )),
                        excerpt
                ));
            }
            return objectMapper.writeValueAsString(new ScenePlan(scenes));
        }

        private String yamlResponse(String prompt) throws JsonProcessingException {
            ScenePlan scenePlan = objectMapper.readValue(
                    between(prompt, "<scene_plan>", "</scene_plan>").strip(),
                    ScenePlan.class
            );
            int chapterCount = Integer.parseInt(extract(Pattern.compile("(?m)^- chapter_count: (\\d+)$"), prompt));
            Set<String> characterNames = new LinkedHashSet<>();
            scenePlan.scenes().forEach(scene -> characterNames.addAll(scene.characters()));
            if (characterNames.isEmpty()) {
                characterNames.add("林溯");
            }

            StringBuilder yaml = new StringBuilder();
            appendQuoted(yaml, "schema_version", "1.0", 0);
            appendQuoted(yaml, "kind", "screenplay_draft", 0);
            yaml.append("metadata:\n");
            appendQuoted(yaml, "title", "星轨回音", 2);
            appendQuoted(yaml, "author", "测试作者", 2);
            appendQuoted(yaml, "logline", "记忆打捞员卷入城市级阴谋。", 2);
            yaml.append("source:\n");
            appendQuoted(yaml, "type", "novel", 2);
            appendNumber(yaml, "chapter_count", chapterCount, 2);
            appendQuoted(yaml, "generation_mode", "ai_pipeline", 2);
            yaml.append("characters:\n");
            int characterIndex = 1;
            for (String characterName : characterNames) {
                yaml.append("  - ");
                appendQuoted(yaml, "id", "character_%03d".formatted(characterIndex++), 0);
                appendQuoted(yaml, "name", characterName, 4);
                appendQuoted(yaml, "role", "主要人物", 4);
                appendQuoted(yaml, "description", characterName + " 参与记忆星尘阴谋线。", 4);
            }
            yaml.append("acts:\n");
            yaml.append("  - ");
            appendQuoted(yaml, "id", "act_1", 0);
            appendQuoted(yaml, "title", "第一幕", 4);
            yaml.append("    chapter_range:\n");
            appendNumber(yaml, "start", 1, 6);
            appendNumber(yaml, "end", chapterCount, 6);
            appendQuoted(yaml, "dramatic_function", "建立记忆打捞委托与先知组织威胁", 4);
            yaml.append("scenes:\n");
            for (ScenePlan.Scene scene : scenePlan.scenes()) {
                yaml.append("  - ");
                appendQuoted(yaml, "id", scene.id(), 0);
                appendQuoted(yaml, "act_id", scene.actId(), 4);
                appendNumber(yaml, "chapter_index", scene.chapterIndex(), 4);
                appendQuoted(yaml, "source_chapter_id", scene.sourceChapterId(), 4);
                appendQuoted(yaml, "title", scene.title(), 4);
                appendQuoted(yaml, "location", scene.location(), 4);
                appendQuoted(yaml, "time", scene.time(), 4);
                appendQuoted(yaml, "scene_purpose", scene.scenePurpose(), 4);
                appendQuoted(yaml, "action", scene.beats().get(0).description() + " 镜头捕捉人物停顿、警觉和转身。", 4);
                yaml.append("    dialogues:\n");
                yaml.append("      - ");
                appendQuoted(yaml, "speaker", scene.characters().isEmpty() ? "林溯" : scene.characters().get(0), 0);
                appendQuoted(yaml, "line", "我们没有退路，只能继续追下去。", 8);
                appendQuoted(yaml, "emotion", "克制", 8);
                appendQuoted(yaml, "source_excerpt", scene.sourceExcerpt(), 4);
                yaml.append("    ai_expansion: true\n");
            }
            return yaml.toString();
        }

        private String extract(Pattern pattern, String value) {
            Matcher matcher = pattern.matcher(value);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Fake LLM 未找到字段：" + pattern);
            }
            return matcher.group(1).trim();
        }

        private String between(String value, String start, String end) {
            int startIndex = value.indexOf(start);
            int endIndex = value.indexOf(end);
            if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) {
                throw new IllegalArgumentException("Fake LLM 未找到片段：" + start);
            }
            return value.substring(startIndex + start.length(), endIndex);
        }

        private String excerpt(String content) {
            String normalized = content.replace("\n", " ").strip();
            return normalized.substring(0, Math.min(60, normalized.length()));
        }

        @SuppressWarnings("unchecked")
        private List<String> stringList(Object value) {
            return value instanceof List<?> values
                    ? values.stream().map(Object::toString).toList()
                    : List.of();
        }

        private String firstString(Object value) {
            List<String> values = stringList(value);
            return values.isEmpty() ? "缺少来源摘录" : values.get(0);
        }

        private String text(Object value) {
            return value == null ? "" : value.toString();
        }

        private void appendQuoted(StringBuilder yaml, String key, String value, int indent) {
            yaml.append(" ".repeat(Math.max(0, indent)));
            yaml.append(key).append(": \"").append(escape(value)).append("\"\n");
        }

        private void appendNumber(StringBuilder yaml, String key, int value, int indent) {
            yaml.append(" ".repeat(Math.max(0, indent)));
            yaml.append(key).append(": ").append(value).append('\n');
        }

        private String escape(String value) {
            return value == null ? "" : value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
