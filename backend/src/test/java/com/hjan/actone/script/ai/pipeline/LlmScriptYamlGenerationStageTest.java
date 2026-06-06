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
import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.model.SourceTrace;
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
    void generateRendersContextAndReturnsCleanYaml() {
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
                            .contains("<global_story_state>")
                            .contains("<scene_plans>")
                            .contains("<cinematic_treatment>")
                            .contains("<dialogue_draft>")
                            .contains("<source_trace>")
                            .contains("\"name\" : \"林夜\"")
                            .contains("\"sceneId\" : \"scene_001\"")
                            .contains("\"sourceExcerpt\" : \"林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。\"")
                            .doesNotContain(
                                    "{{global_story_state}}",
                                    "{{scene_plans}}",
                                    "{{cinematic_treatment}}",
                                    "{{dialogue_draft}}",
                                    "{{source_trace}}"
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
    void generateKeepsSchemaInvalidYamlForPipelineValidatorAndRepair() {
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
                .hasMessage("globalStoryState 不能为空");

        PipelineContext withoutScenePlan = PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withGlobalStoryState(globalStoryState());
        assertThatThrownBy(() -> stage.generate(withoutScenePlan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenePlan 不能为空");

        PipelineContext withoutCinematicTreatment = withoutScenePlan.withScenePlan(scenePlan());
        assertThatThrownBy(() -> stage.generate(withoutCinematicTreatment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cinematicTreatment 不能为空");

        PipelineContext withoutDialogueDraft = withoutCinematicTreatment.withCinematicTreatment(cinematicTreatment());
        assertThatThrownBy(() -> stage.generate(withoutDialogueDraft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dialogueDraft 不能为空");

        PipelineContext withoutSourceTrace = withoutDialogueDraft.withDialogueDraft(dialogueDraft());
        assertThatThrownBy(() -> stage.generate(withoutSourceTrace))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceTrace 不能为空");
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
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan())
                .withCinematicTreatment(cinematicTreatment())
                .withDialogueDraft(dialogueDraft())
                .withSourceTrace(sourceTrace());
    }

    private GlobalStoryState globalStoryState() {
        return new GlobalStoryState(
                List.of(new GlobalStoryState.CharacterProfile(
                        "character_001",
                        "林夜",
                        List.of("刺客"),
                        "主角",
                        "江湖暗刃，临阵改换雇主。",
                        "chapter_1",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.LocationProfile(
                        "location_001",
                        "荒山破庙",
                        "雨夜中的刺杀地点。",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.TimelineEvent(
                        "timeline_001",
                        1,
                        "chapter_1",
                        "雨夜子时",
                        "林夜在破庙等候目标。"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
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
                List.of("character_001"),
                "建立林夜的任务与危险氛围。",
                "刺杀目标与真实目标不一致。",
                List.of(new ScenePlan.Beat(
                        "林夜吹熄残烛，隐入阴影。",
                        "他吹熄了身旁仅存的半截残烛，将自己彻底融入深邃的阴影之中。",
                        List.of("character_001")
                )),
                "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
        )));
    }

    private CinematicTreatment cinematicTreatment() {
        return new CinematicTreatment(List.of(new CinematicTreatment.SceneTreatment(
                "scene_001",
                1,
                "chapter_1",
                "林夜吹熄残烛，指节扣紧刀柄，刀锋在黑暗里出鞘半寸。",
                List.of(new CinematicTreatment.Transformation(
                        "他从冷漠执行者转向主动选择。",
                        "哭声传来，林夜扣紧刀柄的手停在半空。",
                        "动作与停顿",
                        "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。",
                        List.of("character_001")
                ))
        )));
    }

    private DialogueDraft dialogueDraft() {
        return new DialogueDraft(List.of(new DialogueDraft.SceneDialogues(
                "scene_001",
                1,
                "chapter_1",
                List.of(new DialogueDraft.DialogueLine(
                        "林夜",
                        "character_001",
                        "别出声。跟紧我。",
                        "压低声音",
                        "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
                ))
        )));
    }

    private SourceTrace sourceTrace() {
        return new SourceTrace(List.of(new SourceTrace.SceneTrace(
                "scene_001",
                1,
                "chapter_1",
                "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。",
                List.of(new SourceEvidence(
                        "chapter_1",
                        1,
                        "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
                ))
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
