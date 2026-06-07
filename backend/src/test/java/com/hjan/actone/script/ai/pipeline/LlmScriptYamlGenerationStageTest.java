package com.hjan.actone.script.ai.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.ai.model.ChatRole;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.prompt.ClasspathPromptTemplateService;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmScriptYamlGenerationStageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScriptYamlValidator validator = new ScriptYamlValidator();

    @Test
    void generateRendersReducedContextAndReturnsCleanYaml() {
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text("""
                ```yaml
                %s
                ```
                """.formatted(validYaml())));
        LlmScriptYamlGenerationStage stage = stage(llmService);

        String yaml = stage.generate(context());

        assertThat(yaml)
                .startsWith("schema_version:")
                .contains("generation_mode: ai_pipeline")
                .doesNotContain("```");
        ScriptYamlValidationResult validationResult = validator.validate(yaml);
        assertThat(validationResult.valid()).isTrue();

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.TEXT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.15D);
        assertThat(request.topP()).isEqualTo(0.8D);
        assertThat(request.maxTokens()).isEqualTo(4096);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(240));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("<chapter_summaries>")
                            .contains("<scene_plan>")
                            .contains("generation_scope: 完整剧本")
                            .contains("ai_expansion")
                            .contains("first_appearance_chapter_id")
                            .contains("source_chapter_ids")
                            .contains("\"characters\":[\"林夜\"")
                            .contains("\"id\":\"scene_001\"")
                            .contains("\"sourceExcerpt\":\"林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。\"")
                            .doesNotContain(
                                    "{{chapter_summaries}}",
                                    "{{scene_plan}}",
                                    "<global_story_state>",
                                    "<cinematic_treatment>",
                                    "<dialogue_draft>",
                                    "<source_trace>"
                            );
                });
    }

    @Test
    void generateSplitsLargeScenePlanAndMergesYaml() {
        BatchAwareYamlLlmService llmService = new BatchAwareYamlLlmService();
        LlmScriptYamlGenerationStage stage = stage(llmService);

        String yaml = stage.generate(manySceneContext());

        assertThat(llmService.requests()).hasSize(2);
        assertThat(yaml)
                .contains("scene_001", "scene_007")
                .contains("id: act_2", "id: act_3")
                .contains("act_id: act_2", "act_id: act_3")
                .contains("source_chapter_ids:");
        ScriptYamlValidationResult validationResult = validator.validate(yaml);
        assertThat(validationResult.valid()).isTrue();
    }

    @Test
    void generateRejectsBlankModelResponse() {
        LlmScriptYamlGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.generate(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本 YAML 返回为空");
    }

    @Test
    void generatePropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmScriptYamlGenerationStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.generate(context()));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void generateNormalizesSchemaIncompleteYamlBeforeValidation() {
        String invalidYaml = """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "夜雨孤庙"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
                characters: []
                acts: []
                scenes: []
                """;
        LlmScriptYamlGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(invalidYaml)));

        String yaml = stage.generate(context());

        assertThat(yaml).contains("generation_mode: ai_pipeline");
        ScriptYamlValidationResult validationResult = validator.validate(yaml);
        assertThat(validationResult.valid()).isTrue();
        assertThat(yaml)
                .contains("acts:")
                .contains("scenes:")
                .contains("source_excerpt")
                .contains("scene_001");
    }

    @Test
    void generateRejectsContextWithoutRequiredInputs() {
        LlmScriptYamlGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validYaml())));

        assertThatThrownBy(() -> stage.generate(PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chapterSummaries 不能为空");

        PipelineContext withoutScenePlan = PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withChapterSummaries(chapterSummaries());
        assertThatThrownBy(() -> stage.generate(withoutScenePlan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenePlan 不能为空");
    }

    private LlmScriptYamlGenerationStage stage(LLMService llmService) {
        return new LlmScriptYamlGenerationStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                objectMapper
        );
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "夜雨孤庙", "测试作者", "杀手林夜改变刺杀目标。", 3)
                .withChapterSummaries(chapterSummaries())
                .withScenePlan(scenePlan());
    }

    private List<ChapterSummary> chapterSummaries() {
        SourceEvidence evidence = new SourceEvidence(
                "chapter_1",
                1,
                "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
        );
        return List.of(new ChapterSummary(
                "chapter_1",
                1,
                "第一章 夜雨孤庙",
                "林夜在破庙中发现孩子，并开始动摇刺杀任务。",
                List.of(new ChapterSummary.KeyEvent(
                        "event_001",
                        "林夜发现目标不是成年人，而是一个孩子。",
                        List.of(evidence)
                )),
                List.of("林夜", "孩子"),
                List.of("荒山破庙"),
                List.of("雨夜子时"),
                List.of("刺杀任务与救人选择冲突"),
                List.of(new ChapterSummary.InnerState("林夜", "他从冷漠执行者转向主动选择。", evidence)),
                List.of(evidence)
        ));
    }

    private ScenePlan scenePlan() {
        return new ScenePlan(List.of(new ScenePlan.Scene(
                "scene_001",
                "act_1",
                1,
                "chapter_1",
                "破庙伏杀",
                "荒山破庙",
                "雨夜子时",
                List.of("林夜", "孩子"),
                "建立林夜的任务与危险氛围。",
                "刺杀目标与真实目标不一致。",
                List.of(new ScenePlan.Beat(
                        "林夜吹熄残烛，隐入阴影。",
                        "他吹熄了身旁仅存的半截残烛，将自己彻底融入深邃的阴影之中。",
                        List.of("林夜")
                )),
                "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
        )));
    }

    private PipelineContext manySceneContext() {
        return PipelineContext.create("project_1", "夜雨孤庙", "测试作者", "杀手林夜改变刺杀目标。", 7)
                .withChapterSummaries(List.of(
                        summary("chapter_1", 1),
                        summary("chapter_2", 2),
                        summary("chapter_3", 3),
                        summary("chapter_4", 4),
                        summary("chapter_5", 5),
                        summary("chapter_6", 6),
                        summary("chapter_7", 7)
                ))
                .withScenePlan(new ScenePlan(List.of(
                        scene(1),
                        scene(2),
                        scene(3),
                        scene(4),
                        scene(5),
                        scene(6),
                        scene(7)
                )));
    }

    private ChapterSummary summary(String chapterId, int chapterIndex) {
        SourceEvidence evidence = new SourceEvidence(chapterId, chapterIndex, "第 " + chapterIndex + " 章来源摘录。");
        return new ChapterSummary(
                chapterId,
                chapterIndex,
                "第 " + chapterIndex + " 章",
                "第 " + chapterIndex + " 章摘要。",
                List.of(new ChapterSummary.KeyEvent("event_%03d".formatted(chapterIndex), "关键事件。", List.of(evidence))),
                List.of("林夜"),
                List.of("破庙"),
                List.of("雨夜"),
                List.of("选择冲突"),
                List.of(new ChapterSummary.InnerState("林夜", "犹豫。", evidence)),
                List.of(evidence)
        );
    }

    private ScenePlan.Scene scene(int chapterIndex) {
        return new ScenePlan.Scene(
                "scene_%03d".formatted(chapterIndex),
                "act_1",
                chapterIndex,
                "chapter_" + chapterIndex,
                "第 " + chapterIndex + " 场",
                "破庙",
                "雨夜",
                List.of("林夜"),
                "推进第 " + chapterIndex + " 章。",
                "选择冲突。",
                List.of(new ScenePlan.Beat("林夜行动。", "第 " + chapterIndex + " 章来源摘录。", List.of("林夜"))),
                "第 " + chapterIndex + " 章来源摘录。"
        );
    }

    private String validYaml() {
        return """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "夜雨孤庙"
                  author: "测试作者"
                  logline: "杀手林夜改变刺杀目标。"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
                characters:
                  - id: "character_001"
                    name: "林夜"
                    role: "主角"
                    description: "江湖暗刃，临阵改换雇主。"
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 3
                    dramatic_function: "建立刺杀任务并完成立场反转"
                scenes:
                  - id: "scene_001"
                    act_id: "act_1"
                    chapter_index: 1
                    source_chapter_id: "chapter_1"
                    title: "破庙伏杀"
                    location: "荒山破庙"
                    time: "雨夜子时"
                    scene_purpose: "建立林夜的任务与危险氛围。"
                    action: "林夜吹熄残烛，指节扣紧刀柄。"
                    ai_expansion: true
                    dialogues:
                      - speaker: "林夜"
                        line: "别出声。跟紧我。"
                        emotion: "压低声音"
                    source_excerpt: "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
                """;
    }

    private static final class RecordingLlmService implements LLMService {

        private final ChatResponse response;
        private final RuntimeException failure;
        private final List<ChatRequest> requests = new ArrayList<>();

        private RecordingLlmService(ChatResponse response, RuntimeException failure) {
            this.response = response;
            this.failure = failure;
        }

        static RecordingLlmService responding(ChatResponse response) {
            return new RecordingLlmService(response, null);
        }

        static RecordingLlmService failing(RuntimeException failure) {
            return new RecordingLlmService(null, failure);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        List<ChatRequest> requests() {
            return requests;
        }
    }

    private final class BatchAwareYamlLlmService implements LLMService {

        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            try {
                Map<String, Object> scenePlan = objectMapper.readValue(
                        between(request.messages().get(0).content(), "<scene_plan>", "</scene_plan>").strip(),
                        new TypeReference<>() {
                        }
                );
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> scenes = (List<Map<String, Object>>) scenePlan.get("scenes");
                return ChatResponse.text(batchYaml(scenes));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        private List<ChatRequest> requests() {
            return requests;
        }

        private String batchYaml(List<Map<String, Object>> scenes) {
            StringBuilder yaml = new StringBuilder();
            yaml.append("""
                    schema_version: "1.0"
                    kind: "screenplay_draft"
                    metadata:
                      title: "夜雨孤庙"
                    source:
                      type: "novel"
                      chapter_count: 7
                      generation_mode: "ai_pipeline"
                    characters:
                      - id: "character_001"
                        name: "林夜"
                        role: "主角"
                        description: "江湖暗刃。"
                    acts:
                      - id: "act_1"
                        title: "第一幕"
                        chapter_range:
                          start: 1
                          end: 7
                        dramatic_function: "建立选择"
                    scenes:
                    """);
            for (Map<String, Object> scene : scenes) {
                yaml.append("  - id: \"").append(text(scene.get("id"))).append("\"\n");
                yaml.append("    act_id: \"act_1\"\n");
                yaml.append("    chapter_index: ").append(scene.get("chapterIndex")).append('\n');
                yaml.append("    source_chapter_id: \"").append(text(scene.get("sourceChapterId"))).append("\"\n");
                yaml.append("    title: \"").append(text(scene.get("title"))).append("\"\n");
                yaml.append("    location: \"").append(text(scene.get("location"))).append("\"\n");
                yaml.append("    time: \"").append(text(scene.get("time"))).append("\"\n");
                yaml.append("    scene_purpose: \"").append(text(scene.get("scenePurpose"))).append("\"\n");
                yaml.append("    action: \"林夜观察四周。\"\n");
                yaml.append("    dialogues:\n");
                yaml.append("      - speaker: \"林夜\"\n");
                yaml.append("        line: \"继续。\"\n");
                yaml.append("        emotion: \"克制\"\n");
                yaml.append("    source_excerpt: \"").append(text(scene.get("sourceExcerpt"))).append("\"\n");
                yaml.append("    ai_expansion: true\n");
            }
            return yaml.toString();
        }

        private String between(String value, String start, String end) {
            int startIndex = value.indexOf(start);
            int endIndex = value.indexOf(end);
            if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) {
                throw new IllegalArgumentException("缺少片段：" + start);
            }
            return value.substring(startIndex + start.length(), endIndex);
        }

        private String text(Object value) {
            return value == null ? "" : value.toString();
        }
    }
}
