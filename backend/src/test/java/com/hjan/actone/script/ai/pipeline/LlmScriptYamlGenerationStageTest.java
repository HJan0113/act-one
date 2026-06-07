package com.hjan.actone.script.ai.pipeline;

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
                .startsWith("schema_version: \"1.0\"")
                .contains("generation_mode: \"ai_pipeline\"")
                .doesNotContain("```");
        ScriptYamlValidationResult validationResult = validator.validate(yaml);
        assertThat(validationResult.valid()).isTrue();

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.TEXT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.15D);
        assertThat(request.topP()).isEqualTo(0.8D);
        assertThat(request.maxTokens()).isEqualTo(8192);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("<chapter_summaries>")
                            .contains("<scene_plan>")
                            .contains("ai_expansion")
                            .contains("\"characters\" : [ \"林夜\"")
                            .contains("\"id\" : \"scene_001\"")
                            .contains("\"sourceExcerpt\" : \"林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。\"")
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
    void generateKeepsSchemaInvalidYamlForPipelineValidatorAndFallback() {
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

        assertThat(yaml).contains("generation_mode: \"ai_pipeline\"");
        ScriptYamlValidationResult validationResult = validator.validate(yaml);
        assertThat(validationResult.valid()).isFalse();
        assertThat(validationResult.errorMessage()).contains("acts 至少需要 1 个幕");
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

    private LlmScriptYamlGenerationStage stage(RecordingLlmService llmService) {
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
}
