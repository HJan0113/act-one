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
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmScenePlanningStageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void planRendersChapterSummariesAndParsesScenePlan() {
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text(validScenePlanJson()));
        LlmScenePlanningStage stage = stage(llmService);

        ScenePlan plan = stage.plan(context());

        assertThat(plan.scenes()).hasSize(3);
        assertThat(plan.scenes()).extracting(ScenePlan.Scene::id)
                .containsExactly("scene_001", "scene_002", "scene_003");
        assertThat(plan.scenes()).extracting(ScenePlan.Scene::sourceChapterId)
                .containsExactly("chapter_1", "chapter_2", "chapter_3");
        assertThat(plan.scenes().get(0).characters()).containsExactly("林夜", "孩子");

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.JSON_OBJECT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.2D);
        assertThat(request.topP()).isEqualTo(0.8D);
        assertThat(request.maxTokens()).isEqualTo(8192);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("<chapter_summaries>")
                            .contains("\"chapterId\":\"chapter_1\"")
                            .doesNotContain("<global_story_state>")
                            .doesNotContain("{{chapter_summaries}}");
                });
    }

    @Test
    void planSplitsLargeSummaryListAndReindexesMergedScenes() {
        BatchAwareScenePlanLlmService llmService = new BatchAwareScenePlanLlmService();
        LlmScenePlanningStage stage = stage(llmService);

        ScenePlan plan = stage.plan(PipelineContext.create("project_1", "夜雨孤庙", null, null, 4)
                .withChapterSummaries(List.of(
                        summary("chapter_1", 1, "第一章事件。"),
                        summary("chapter_2", 2, "第二章事件。"),
                        summary("chapter_3", 3, "第三章事件。"),
                        summary("chapter_4", 4, "第四章事件。")
                )));

        assertThat(llmService.requests()).hasSize(2);
        assertThat(plan.scenes()).extracting(ScenePlan.Scene::id)
                .containsExactly("scene_001", "scene_002", "scene_003", "scene_004");
        assertThat(plan.scenes()).extracting(ScenePlan.Scene::sourceChapterId)
                .containsExactly("chapter_1", "chapter_2", "chapter_3", "chapter_4");
    }

    @Test
    void planRejectsBlankModelResponse() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.plan(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 场景规划返回为空");
    }

    @Test
    void planPropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmScenePlanningStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.plan(context()));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void planRejectsIllegalStructureWithoutExposingRawBody() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {"scenes":[{"id":"","title":"api-key=secret 原始小说正文"}]}
                """)));

        assertThatThrownBy(() -> stage.plan(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 场景规划结构非法")
                .hasMessageNotContaining("api-key")
                .hasMessageNotContaining("原始小说正文");
    }

    @Test
    void planRejectsUnknownSourceChapter() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validScenePlanJson().replace("\"sourceChapterId\":\"chapter_3\"", "\"sourceChapterId\":\"chapter_9\"")
        )));

        assertThatThrownBy(() -> stage.plan(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 场景规划来源章节不一致");
    }

    @Test
    void planRejectsChapterIndexMismatch() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validScenePlanJson().replace(
                        "\"chapterIndex\":2,\"sourceChapterId\":\"chapter_2\"",
                        "\"chapterIndex\":3,\"sourceChapterId\":\"chapter_2\""
                )
        )));

        assertThatThrownBy(() -> stage.plan(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 场景规划章节索引不一致");
    }

    @Test
    void planRejectsUnstableSceneIds() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validScenePlanJson().replace("\"id\":\"scene_002\"", "\"id\":\"scene_009\"")
        )));

        assertThatThrownBy(() -> stage.plan(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 场景规划场景 ID 不稳定");
    }

    @Test
    void planRejectsMissingChapterCoverage() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validScenePlanJson().replace(
                        "\"chapterIndex\":3,\"sourceChapterId\":\"chapter_3\"",
                        "\"chapterIndex\":2,\"sourceChapterId\":\"chapter_2\""
                )
        )));

        assertThatThrownBy(() -> stage.plan(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 场景规划缺少章节场景");
    }

    @Test
    void planRejectsContextWithoutChapterSummaries() {
        LlmScenePlanningStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validScenePlanJson())));

        assertThatThrownBy(() -> stage.plan(PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chapterSummaries 不能为空");
    }

    private LlmScenePlanningStage stage(LLMService llmService) {
        return new LlmScenePlanningStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                objectMapper
        );
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withChapterSummaries(chapterSummaries());
    }

    private List<ChapterSummary> chapterSummaries() {
        return List.of(
                summary("chapter_1", 1, "林夜在破庙中发现孩子并放下短刀。"),
                summary("chapter_2", 2, "黑衣人追入破庙，逼林夜交出孩子。"),
                summary("chapter_3", 3, "林夜在雨停前决定护送孩子离开。")
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
                List.of("林夜", "孩子"),
                List.of("破庙"),
                List.of("雨夜"),
                List.of("刺杀任务与救人选择冲突"),
                List.of(new ChapterSummary.InnerState("林夜", "他犹豫是否继续执行任务。", evidence)),
                List.of(evidence)
        );
    }

    private String validScenePlanJson() {
        return """
                {
                  "scenes":[
                    {
                      "id":"scene_001","actId":"act_1","chapterIndex":1,"sourceChapterId":"chapter_1",
                      "title":"破庙初遇","location":"荒山破庙","time":"雨夜子时",
                      "characters":["林夜","孩子"],
                      "scenePurpose":"建立刺杀任务与真实目标的错位。","conflict":"林夜是否继续执行刺杀。",
                      "beats":[
                        {
                          "description":"林夜握刀逼近角落，听见孩子压低的哭声。",
                          "sourceExcerpt":"林夜在破庙中发现孩子并放下短刀。",
                          "characterIds":["林夜","孩子"]
                        }
                      ],
                      "sourceExcerpt":"林夜在破庙中发现孩子并放下短刀。"
                    },
                    {
                      "id":"scene_002","actId":"act_1","chapterIndex":2,"sourceChapterId":"chapter_2",
                      "title":"追兵入庙","location":"破庙门口","time":"同夜",
                      "characters":["林夜","孩子","黑衣人"],"scenePurpose":"施加外部压力。","conflict":"是否交出孩子。",
                      "beats":[{"description":"黑衣人逼林夜交人。","sourceExcerpt":"黑衣人追入破庙，逼林夜交出孩子。","characterIds":["林夜","黑衣人"]}],
                      "sourceExcerpt":"黑衣人追入破庙，逼林夜交出孩子。"
                    },
                    {
                      "id":"scene_003","actId":"act_1","chapterIndex":3,"sourceChapterId":"chapter_3",
                      "title":"雨停前离开","location":"破庙外山道","time":"雨停前",
                      "characters":["林夜","孩子"],"scenePurpose":"主角做出行动。","conflict":"是否承担护送风险。",
                      "beats":[{"description":"林夜带孩子离开。","sourceExcerpt":"林夜在雨停前决定护送孩子离开。","characterIds":["林夜","孩子"]}],
                      "sourceExcerpt":"林夜在雨停前决定护送孩子离开。"
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

    private final class BatchAwareScenePlanLlmService implements LLMService {

        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            String prompt = request.messages().get(0).content();
            List<ChapterSummary> summaries = List.of(
                            summary("chapter_1", 1, "第一章事件。"),
                            summary("chapter_2", 2, "第二章事件。"),
                            summary("chapter_3", 3, "第三章事件。"),
                            summary("chapter_4", 4, "第四章事件。")
                    ).stream()
                    .filter(summary -> prompt.contains("\"chapterId\":\"" + summary.chapterId() + "\""))
                    .toList();
            List<ScenePlan.Scene> scenes = new ArrayList<>();
            for (ChapterSummary summary : summaries) {
                scenes.add(new ScenePlan.Scene(
                        "scene_%03d".formatted(scenes.size() + 1),
                        "act_1",
                        summary.chapterIndex(),
                        summary.chapterId(),
                        summary.title(),
                        "破庙",
                        "雨夜",
                        List.of("林夜"),
                        "推进本章核心事件。",
                        "主角面对选择。",
                        List.of(new ScenePlan.Beat(summary.summary(), summary.sourceEvidence().get(0).excerpt(), List.of("林夜"))),
                        summary.sourceEvidence().get(0).excerpt()
                ));
            }
            try {
                return ChatResponse.text(objectMapper.writeValueAsString(new ScenePlan(scenes)));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        private List<ChatRequest> requests() {
            return requests;
        }
    }
}
