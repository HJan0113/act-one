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
import com.hjan.actone.script.ai.model.DialogueDraft;
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

class LlmDialogueGenerationStageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void draftRendersContextAndParsesDialogueDraft() {
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text(validDialogueJson()));
        LlmDialogueGenerationStage stage = stage(llmService);

        DialogueDraft draft = stage.draft(context());

        assertThat(draft.scenes()).hasSize(2);
        assertThat(draft.scenes().get(0).dialogues()).hasSize(3);
        assertThat(draft.scenes().get(0).dialogues()).extracting(DialogueDraft.DialogueLine::speaker)
                .containsExactly("林夜", "孩子", "旁白");
        assertThat(draft.scenes().get(1).dialogues()).extracting(DialogueDraft.DialogueLine::speaker)
                .containsExactly("黑衣人", "林夜");

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.JSON_OBJECT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.3D);
        assertThat(request.topP()).isEqualTo(0.85D);
        assertThat(request.maxTokens()).isEqualTo(4096);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("<global_story_state>")
                            .contains("<scene_plan>")
                            .contains("<cinematic_treatment>")
                            .contains("\"name\" : \"林夜\"")
                            .contains("\"sceneId\" : \"scene_001\"")
                            .doesNotContain("{{global_story_state}}", "{{scene_plan}}", "{{cinematic_treatment}}");
                });
    }

    @Test
    void draftRejectsBlankModelResponse() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白返回为空");
    }

    @Test
    void draftPropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.draft(context()));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void draftRejectsIllegalStructureWithoutExposingRawBody() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {"scenes":[{"sceneId":"","dialogues":[{"speaker":"api-key=secret 原始小说正文"}]}]}
                """)));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白结构非法")
                .hasMessageNotContaining("api-key")
                .hasMessageNotContaining("原始小说正文");
    }

    @Test
    void draftRejectsUnknownScene() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson().replace("\"sceneId\":\"scene_002\"", "\"sceneId\":\"scene_009\"")
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白场景引用非法");
    }

    @Test
    void draftRejectsMissingSceneDialogues() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {
                  "scenes":[
                    {
                      "sceneId":"scene_001","chapterIndex":1,"sourceChapterId":"chapter_1",
                      "dialogues":[{"speaker":"林夜","speakerCharacterId":"character_001","line":"别出声。","emotion":"压低声音","sourceExcerpt":"握刀的手微微停住。"}]
                    }
                  ]
                }
                """)));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白缺少场景对白");
    }

    @Test
    void draftRejectsSceneSourceMismatch() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson().replace(
                        "\"sceneId\":\"scene_002\",\"chapterIndex\":2,\"sourceChapterId\":\"chapter_2\"",
                        "\"sceneId\":\"scene_002\",\"chapterIndex\":1,\"sourceChapterId\":\"chapter_1\""
                )
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白场景来源不一致");
    }

    @Test
    void draftRejectsMissingSpeakerCharacterIdForCharacterLine() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson().replace("\"speakerCharacterId\":\"character_001\"", "\"speakerCharacterId\":null")
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白缺少 speakerCharacterId");
    }

    @Test
    void draftRejectsUnknownSpeakerCharacter() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson().replace("\"speakerCharacterId\":\"character_001\"", "\"speakerCharacterId\":\"character_999\"")
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白人物引用非法");
    }

    @Test
    void draftRejectsSpeakerCharacterOutsideScene() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson().replace(
                        "\"speaker\":\"孩子\",\"speakerCharacterId\":\"character_002\"",
                        "\"speaker\":\"黑衣人\",\"speakerCharacterId\":\"character_003\""
                )
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白人物不在场景中");
    }

    @Test
    void draftRejectsSpeakerNameMismatch() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson().replace(
                        "\"speaker\":\"林夜\",\"speakerCharacterId\":\"character_001\"",
                        "\"speaker\":\"孩子\",\"speakerCharacterId\":\"character_001\""
                )
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白 speaker 不一致");
    }

    @Test
    void draftRejectsIdenticalLinesForDifferentSpeakersInSameScene() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validDialogueJson()
                        .replace("\"line\":\"跟紧我，别出声。\"", "\"line\":\"别动。\"")
                        .replace("\"line\":\"你为什么救我？\"", "\"line\":\"别动。\"")
        )));

        assertThatThrownBy(() -> stage.draft(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 剧本对白缺少角色区分");
    }

    @Test
    void draftRejectsContextWithoutRequiredInputs() {
        LlmDialogueGenerationStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validDialogueJson())));

        assertThatThrownBy(() -> stage.draft(PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("globalStoryState 不能为空");

        PipelineContext withoutScenePlan = PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withGlobalStoryState(globalStoryState());
        assertThatThrownBy(() -> stage.draft(withoutScenePlan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenePlan 不能为空");

        PipelineContext withoutCinematicTreatment = PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan());
        assertThatThrownBy(() -> stage.draft(withoutCinematicTreatment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cinematicTreatment 不能为空");
    }

    private LlmDialogueGenerationStage stage(RecordingLlmService llmService) {
        return new LlmDialogueGenerationStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                objectMapper
        );
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan())
                .withCinematicTreatment(cinematicTreatment());
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
                        ),
                        new GlobalStoryState.CharacterProfile(
                                "character_003",
                                "黑衣人",
                                List.of("追兵"),
                                "反派",
                                "逼迫林夜完成刺杀任务。",
                                "chapter_2",
                                List.of("chapter_2")
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
                        List.of("character_001", "character_003"),
                        "外部压力逼迫主角表态。",
                        "黑衣人要求交出孩子。",
                        List.of(new ScenePlan.Beat(
                                "庙门被踢开，林夜挡在孩子前方。",
                                "黑衣人逼近，林夜决定挡住对方。",
                                List.of("character_001", "character_003")
                        )),
                        "黑衣人逼近，林夜决定挡住对方。"
                )
        ));
    }

    private CinematicTreatment cinematicTreatment() {
        return new CinematicTreatment(List.of(
                new CinematicTreatment.SceneTreatment(
                        "scene_001",
                        1,
                        "chapter_1",
                        "林夜把孩子护到身后，握刀的手停在半空，刀尖慢慢垂下。",
                        List.of(new CinematicTreatment.Transformation(
                                "他犹豫是否继续执行任务。",
                                "林夜停住脚步，刀尖慢慢垂下。",
                                "动作与停顿",
                                "握刀的手微微停住。",
                                List.of("character_001")
                        ))
                ),
                new CinematicTreatment.SceneTreatment(
                        "scene_002",
                        2,
                        "chapter_2",
                        "庙门被踢开，林夜挡住黑衣人的去路。",
                        List.of()
                )
        ));
    }

    private String validDialogueJson() {
        return """
                {
                  "scenes":[
                    {
                      "sceneId":"scene_001","chapterIndex":1,"sourceChapterId":"chapter_1",
                      "dialogues":[
                        {"speaker":"林夜","speakerCharacterId":"character_001","line":"跟紧我，别出声。","emotion":"压低声音","sourceExcerpt":"握刀的手微微停住。"},
                        {"speaker":"孩子","speakerCharacterId":"character_002","line":"你为什么救我？","emotion":"怯生生","sourceExcerpt":"林夜发现孩子并开始犹豫。"},
                        {"speaker":"旁白","speakerCharacterId":null,"line":"雨声压住了庙外逼近的脚步。","emotion":"低沉","sourceExcerpt":"荒山破庙，雨夜子时。"}
                      ]
                    },
                    {
                      "sceneId":"scene_002","chapterIndex":2,"sourceChapterId":"chapter_2",
                      "dialogues":[
                        {"speaker":"黑衣人","speakerCharacterId":"character_003","line":"让开。那孩子不是你能护的。","emotion":"威胁","sourceExcerpt":"黑衣人逼近，林夜决定挡住对方。"},
                        {"speaker":"林夜","speakerCharacterId":"character_001","line":"从现在起，是我说了算。","emotion":"冷静","sourceExcerpt":"庙门被踢开，林夜挡住黑衣人的去路。"}
                      ]
                    }
                  ]
                }
                """;
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
                List.of(),
                List.of(evidence)
        );
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
