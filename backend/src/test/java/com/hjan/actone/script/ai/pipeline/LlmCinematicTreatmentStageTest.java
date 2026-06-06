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
import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmCinematicTreatmentStageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void visualizeRendersContextAndParsesCinematicTreatment() {
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text(validTreatmentJson()));
        LlmCinematicTreatmentStage stage = stage(llmService);

        CinematicTreatment treatment = stage.visualize(context());

        assertThat(treatment.scenes()).hasSize(2);
        assertThat(treatment.scenes().get(0).sceneId()).isEqualTo("scene_001");
        assertThat(treatment.scenes().get(0).action()).contains("刀尖慢慢垂下");
        assertThat(treatment.scenes().get(0).transformations()).singleElement()
                .satisfies(transformation -> {
                    assertThat(transformation.originalText()).contains("犹豫");
                    assertThat(transformation.visualizedText()).contains("刀尖慢慢垂下");
                    assertThat(transformation.characterIds()).containsExactly("character_001");
                });

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.JSON_OBJECT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.2D);
        assertThat(request.topP()).isEqualTo(0.8D);
        assertThat(request.maxTokens()).isEqualTo(4096);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("<chapter_summaries>")
                            .contains("<global_story_state>")
                            .contains("<scene_plan>")
                            .contains("\"innerStates\"")
                            .contains("\"id\" : \"scene_001\"")
                            .doesNotContain("{{chapter_summaries}}", "{{global_story_state}}", "{{scene_plan}}");
                });
    }

    @Test
    void visualizeRejectsBlankModelResponse() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化返回为空");
    }

    @Test
    void visualizePropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.visualize(context()));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void visualizeRejectsIllegalStructureWithoutExposingRawBody() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {"scenes":[{"sceneId":"","action":"api-key=secret 原始小说正文"}]}
                """)));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化结构非法")
                .hasMessageNotContaining("api-key")
                .hasMessageNotContaining("原始小说正文");
    }

    @Test
    void visualizeRejectsUnknownScene() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validTreatmentJson().replace("\"sceneId\":\"scene_002\"", "\"sceneId\":\"scene_009\"")
        )));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化场景引用非法");
    }

    @Test
    void visualizeRejectsMissingSceneTreatment() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {
                  "scenes":[
                    {
                      "sceneId":"scene_001","chapterIndex":1,"sourceChapterId":"chapter_1",
                      "action":"林夜停住脚步，刀尖慢慢垂下。",
                      "transformations":[{"originalText":"他犹豫是否继续执行任务。","visualizedText":"林夜停住脚步，刀尖慢慢垂下。","technique":"动作","sourceExcerpt":"握刀的手微微停住","characterIds":["character_001"]}]
                    }
                  ]
                }
                """)));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化缺少场景处理");
    }

    @Test
    void visualizeRejectsSceneSourceMismatch() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validTreatmentJson().replace(
                        "\"sceneId\":\"scene_002\",\"chapterIndex\":2,\"sourceChapterId\":\"chapter_2\"",
                        "\"sceneId\":\"scene_002\",\"chapterIndex\":1,\"sourceChapterId\":\"chapter_1\""
                )
        )));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化场景来源不一致");
    }

    @Test
    void visualizeRejectsUnknownTransformationCharacter() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validTreatmentJson().replace("\"characterIds\":[\"character_001\"]", "\"characterIds\":[\"character_999\"]")
        )));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化人物引用非法");
    }

    @Test
    void visualizeRejectsTransformationWithoutCharacterReference() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validTreatmentJson().replace("\"characterIds\":[\"character_001\"]", "\"characterIds\":[]")
        )));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化缺少人物引用");
    }

    @Test
    void visualizeRejectsUnchangedRewrite() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validTreatmentJson().replace(
                        "\"visualizedText\":\"林夜停住脚步，刀尖慢慢垂下。\"",
                        "\"visualizedText\":\"他犹豫是否继续执行任务。\""
                )
        )));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化缺少有效改写");
    }

    @Test
    void visualizeRejectsInteriorNarrationInVisualizedText() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validTreatmentJson().replace(
                        "\"visualizedText\":\"林夜停住脚步，刀尖慢慢垂下。\"",
                        "\"visualizedText\":\"林夜内心感到犹豫。\""
                )
        )));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化仍包含不可拍心理");
    }

    @Test
    void visualizeRejectsTreatmentWithoutRewriteComparison() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {
                  "scenes":[
                    {"sceneId":"scene_001","chapterIndex":1,"sourceChapterId":"chapter_1","action":"林夜停住脚步，刀尖慢慢垂下。","transformations":[]},
                    {"sceneId":"scene_002","chapterIndex":2,"sourceChapterId":"chapter_2","action":"黑衣人踢开庙门，林夜挡在孩子前方。","transformations":[]}
                  ]
                }
                """)));

        assertThatThrownBy(() -> stage.visualize(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 心理影视化缺少改写对照");
    }

    @Test
    void visualizeRejectsContextWithoutRequiredInputs() {
        LlmCinematicTreatmentStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validTreatmentJson())));

        assertThatThrownBy(() -> stage.visualize(PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chapterSummaries 不能为空");

        PipelineContext withoutGlobalStoryState = PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withChapterSummaries(chapterSummaries())
                .withScenePlan(scenePlan());
        assertThatThrownBy(() -> stage.visualize(withoutGlobalStoryState))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("globalStoryState 不能为空");

        PipelineContext withoutScenePlan = PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withChapterSummaries(chapterSummaries())
                .withGlobalStoryState(globalStoryState());
        assertThatThrownBy(() -> stage.visualize(withoutScenePlan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenePlan 不能为空");
    }

    private LlmCinematicTreatmentStage stage(RecordingLlmService llmService) {
        return new LlmCinematicTreatmentStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                objectMapper
        );
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withChapterSummaries(chapterSummaries())
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan());
    }

    private List<ChapterSummary> chapterSummaries() {
        return List.of(
                summary("chapter_1", 1, "林夜发现孩子并开始犹豫。"),
                summary("chapter_2", 2, "黑衣人逼近，林夜决定挡住对方。")
        );
    }

    private ChapterSummary summary(String chapterId, int chapterIndex, String excerpt) {
        SourceEvidence evidence = new SourceEvidence(chapterId, chapterIndex, excerpt);
        return new ChapterSummary(
                chapterId,
                chapterIndex,
                "第 " + chapterIndex + " 章",
                excerpt,
                List.of(new ChapterSummary.KeyEvent(
                        "event_%03d".formatted(chapterIndex),
                        excerpt,
                        List.of(evidence)
                )),
                List.of("林夜"),
                List.of("破庙"),
                List.of("雨夜"),
                List.of("刺杀任务与救人选择冲突"),
                List.of(new ChapterSummary.InnerState("林夜", "他犹豫是否继续执行任务。", evidence)),
                List.of(evidence)
        );
    }

    private GlobalStoryState globalStoryState() {
        return new GlobalStoryState(
                List.of(
                        new GlobalStoryState.CharacterProfile(
                                "character_001",
                                "林夜",
                                List.of("刺客"),
                                "主角",
                                "原本执行刺杀任务，后选择保护孩子。",
                                "chapter_1",
                                List.of("chapter_1", "chapter_2")
                        ),
                        new GlobalStoryState.CharacterProfile(
                                "character_002",
                                "孩子",
                                List.of(),
                                "被保护者",
                                "刺杀任务的真正目标。",
                                "chapter_1",
                                List.of("chapter_1", "chapter_2")
                        )
                ),
                List.of(new GlobalStoryState.LocationProfile(
                        "location_001",
                        "破庙",
                        "雨夜中的冲突发生地。",
                        List.of("chapter_1", "chapter_2")
                )),
                List.of(new GlobalStoryState.TimelineEvent(
                        "timeline_001",
                        1,
                        "chapter_1",
                        "雨夜",
                        "林夜在破庙发现孩子。"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private ScenePlan scenePlan() {
        return new ScenePlan(List.of(
                new ScenePlan.Scene(
                        "scene_001",
                        "act_1",
                        1,
                        "chapter_1",
                        "短刀落地",
                        "荒山破庙",
                        "雨夜子时",
                        List.of("character_001", "character_002"),
                        "完成林夜第一次选择。",
                        "任务命令与救人本能冲突。",
                        List.of(new ScenePlan.Beat(
                                "林夜把短刀压回袖中，挡在孩子身前。",
                                "握刀的手微微停住。",
                                List.of("character_001", "character_002")
                        )),
                        "林夜发现孩子并开始犹豫。"
                ),
                new ScenePlan.Scene(
                        "scene_002",
                        "act_1",
                        2,
                        "chapter_2",
                        "追兵入庙",
                        "破庙门口",
                        "同夜",
                        List.of("character_001", "character_002"),
                        "外部压力逼迫主角表态。",
                        "黑衣人要求交出孩子。",
                        List.of(new ScenePlan.Beat(
                                "庙门被踢开，林夜挡在孩子前方。",
                                "黑衣人逼近，林夜决定挡住对方。",
                                List.of("character_001", "character_002")
                        )),
                        "黑衣人逼近，林夜决定挡住对方。"
                )
        ));
    }

    private String validTreatmentJson() {
        return """
                {
                  "scenes":[
                    {
                      "sceneId":"scene_001","chapterIndex":1,"sourceChapterId":"chapter_1",
                      "action":"林夜把孩子护到身后，握刀的手停在半空，刀尖慢慢垂下。",
                      "transformations":[
                        {
                          "originalText":"他犹豫是否继续执行任务。",
                          "visualizedText":"林夜停住脚步，刀尖慢慢垂下。",
                          "technique":"动作与停顿",
                          "sourceExcerpt":"握刀的手微微停住。",
                          "characterIds":["character_001"]
                        }
                      ]
                    },
                    {
                      "sceneId":"scene_002","chapterIndex":2,"sourceChapterId":"chapter_2",
                      "action":"庙门被踢开，林夜挡在孩子前方，雨水沿刀鞘滴落。",
                      "transformations":[]
                    }
                  ]
                }
                """;
    }

    private static final class RecordingLlmService implements LLMService {

        private final List<ChatRequest> requests = new ArrayList<>();
        private final ChatResponse response;
        private final RuntimeException failure;

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
            return List.copyOf(requests);
        }
    }
}
