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
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.SourceEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmGlobalStoryExtractionStageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractRendersChapterSummariesAndParsesGlobalStoryState() {
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text(validStateJson()));
        LlmGlobalStoryExtractionStage stage = stage(llmService);

        GlobalStoryState state = stage.extract(context());

        assertThat(state.characters()).extracting(GlobalStoryState.CharacterProfile::id)
                .containsExactly("character_001", "character_002");
        assertThat(state.locations()).singleElement()
                .satisfies(location -> {
                    assertThat(location.id()).isEqualTo("location_001");
                    assertThat(location.sourceChapterIds()).containsExactly("chapter_1", "chapter_2");
                });
        assertThat(state.timeline()).hasSize(3);
        assertThat(state.relationships()).singleElement()
                .satisfies(relationship -> {
                    assertThat(relationship.sourceCharacterId()).isEqualTo("character_001");
                    assertThat(relationship.targetCharacterId()).isEqualTo("character_002");
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
                            .contains("\"chapterId\" : \"chapter_1\"")
                            .contains("林夜在破庙中听见孩子哭声")
                            .doesNotContain("{{chapter_summaries}}");
                });
    }

    @Test
    void extractRejectsBlankModelResponse() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.extract(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 全局故事状态返回为空");
    }

    @Test
    void extractPropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.extract(context()));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void extractRejectsIllegalStructureWithoutExposingRawBody() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {"characters":[{"id":"","name":"api-key=secret 原始小说正文"}]}
                """)));

        assertThatThrownBy(() -> stage.extract(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 全局故事状态结构非法")
                .hasMessageNotContaining("api-key")
                .hasMessageNotContaining("原始小说正文");
    }

    @Test
    void extractRejectsMissingCoreFacts() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {
                  "characters":[],
                  "locations":[],
                  "timeline":[],
                  "relationships":[],
                  "props":[],
                  "foreshadowing":[],
                  "conflicts":[]
                }
                """)));

        assertThatThrownBy(() -> stage.extract(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 全局故事状态缺少核心事实");
    }

    @Test
    void extractRejectsUnknownSourceChapter() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validStateJson().replace("\"chapter_3\"", "\"chapter_9\"")
        )));

        assertThatThrownBy(() -> stage.extract(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 全局故事状态来源章节不一致");
    }

    @Test
    void extractRejectsMissingSourceChapterList() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validStateJson().replace(
                        "\"sourceChapterIds\":[\"chapter_1\",\"chapter_2\"]",
                        "\"sourceChapterIds\":[]"
                )
        )));

        assertThatThrownBy(() -> stage.extract(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 全局故事状态缺少来源章节");
    }

    @Test
    void extractRejectsUnknownRelationshipCharacter() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validStateJson().replace("\"targetCharacterId\":\"character_002\"", "\"targetCharacterId\":\"character_999\"")
        )));

        assertThatThrownBy(() -> stage.extract(context()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 全局故事状态人物关系引用非法");
    }

    @Test
    void extractRejectsContextWithoutChapterSummaries() {
        LlmGlobalStoryExtractionStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validStateJson())));

        assertThatThrownBy(() -> stage.extract(PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chapterSummaries 不能为空");
    }

    private LlmGlobalStoryExtractionStage stage(RecordingLlmService llmService) {
        return new LlmGlobalStoryExtractionStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                objectMapper
        );
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "夜雨孤庙", null, null, 3)
                .withChapterSummaries(List.of(
                        summary("chapter_1", 1, "林夜在破庙中听见孩子哭声。"),
                        summary("chapter_2", 2, "黑衣人追入破庙，逼林夜交出孩子。"),
                        summary("chapter_3", 3, "林夜在雨停前决定护送孩子离开。")
                ));
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

    private String validStateJson() {
        return """
                {
                  "characters":[
                    {
                      "id":"character_001",
                      "name":"林夜",
                      "aliases":["刺客"],
                      "role":"主角",
                      "description":"原本执行刺杀任务，后选择保护孩子。",
                      "firstAppearanceChapterId":"chapter_1",
                      "sourceChapterIds":["chapter_1","chapter_2","chapter_3"]
                    },
                    {
                      "id":"character_002",
                      "name":"孩子",
                      "aliases":[],
                      "role":"被保护者",
                      "description":"刺杀任务的真正目标。",
                      "firstAppearanceChapterId":"chapter_1",
                      "sourceChapterIds":["chapter_1","chapter_2","chapter_3"]
                    }
                  ],
                  "locations":[
                    {
                      "id":"location_001",
                      "name":"破庙",
                      "description":"雨夜中的冲突发生地。",
                      "sourceChapterIds":["chapter_1","chapter_2"]
                    }
                  ],
                  "timeline":[
                    {
                      "eventId":"timeline_001",
                      "order":1,
                      "chapterId":"chapter_1",
                      "timeExpression":"雨夜",
                      "description":"林夜在破庙听见孩子哭声。"
                    },
                    {
                      "eventId":"timeline_002",
                      "order":2,
                      "chapterId":"chapter_2",
                      "timeExpression":"同夜",
                      "description":"黑衣人追入破庙。"
                    },
                    {
                      "eventId":"timeline_003",
                      "order":3,
                      "chapterId":"chapter_3",
                      "timeExpression":"雨停前",
                      "description":"林夜决定护送孩子离开。"
                    }
                  ],
                  "relationships":[
                    {
                      "id":"relationship_001",
                      "sourceCharacterId":"character_001",
                      "targetCharacterId":"character_002",
                      "type":"保护",
                      "description":"林夜从刺杀者转为孩子的保护者。",
                      "sourceChapterIds":["chapter_1","chapter_3"]
                    }
                  ],
                  "props":[
                    {
                      "id":"prop_001",
                      "name":"短刀",
                      "description":"林夜执行任务的武器，也体现他的犹豫。",
                      "sourceChapterIds":["chapter_1"]
                    }
                  ],
                  "foreshadowing":[
                    {
                      "id":"foreshadowing_001",
                      "description":"孩子的身份可能关联雇主真实目的。",
                      "payoffHint":"后续揭示雇主反转。",
                      "sourceChapterIds":["chapter_1","chapter_2"]
                    }
                  ],
                  "conflicts":[
                    {
                      "id":"conflict_001",
                      "description":"林夜的任务与道德选择冲突。",
                      "candidateIds":["character_001","character_002"],
                      "sourceChapterIds":["chapter_1","chapter_2","chapter_3"]
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
