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
import com.hjan.actone.script.model.NovelChapter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmChapterSummaryStageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void summarizeRendersPromptAndParsesStructuredChapterSummary() {
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text(validSummaryJson()));
        LlmChapterSummaryStage stage = stage(llmService);

        ChapterSummary summary = stage.summarize(chapter());

        assertThat(summary.chapterId()).isEqualTo("chapter_1");
        assertThat(summary.chapterIndex()).isEqualTo(1);
        assertThat(summary.keyEvents()).hasSize(1);
        assertThat(summary.characters()).contains("林夜", "孩子");
        assertThat(summary.sourceEvidence()).singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.sourceChapterId()).isEqualTo("chapter_1");
                    assertThat(evidence.chapterIndex()).isEqualTo(1);
                    assertThat(evidence.excerpt()).contains("孩子哭声");
                });

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.JSON_OBJECT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.2D);
        assertThat(request.topP()).isEqualTo(0.8D);
        assertThat(request.maxTokens()).isEqualTo(2048);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("chapter_id: chapter_1")
                            .contains("chapter_index: 1")
                            .contains("chapter_title: 第一章 雨夜")
                            .contains("林夜听见孩子哭声")
                            .doesNotContain("{{chapter_id}}", "{{chapter_content}}");
                });
    }

    @Test
    void summarizeRejectsBlankModelResponse() {
        LlmChapterSummaryStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.summarize(chapter()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 章节摘要返回为空");
    }

    @Test
    void summarizePropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmChapterSummaryStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.summarize(chapter()));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void summarizeRejectsIllegalStructureWithoutExposingRawBody() {
        LlmChapterSummaryStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {"chapterId":"chapter_1","chapterIndex":1,"summary":"api-key=secret 原始小说正文"}
                """)));

        assertThatThrownBy(() -> stage.summarize(chapter()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 章节摘要结构非法")
                .hasMessageNotContaining("api-key")
                .hasMessageNotContaining("原始小说正文");
    }

    @Test
    void summarizeRejectsMismatchedChapterIdentity() {
        LlmChapterSummaryStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validSummaryJson().replace("\"chapterId\":\"chapter_1\"", "\"chapterId\":\"chapter_2\"")
        )));

        assertThatThrownBy(() -> stage.summarize(chapter()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 章节摘要章节引用不一致");
    }

    @Test
    void summarizeRejectsMismatchedSourceEvidence() {
        LlmChapterSummaryStage stage = stage(RecordingLlmService.responding(ChatResponse.text(
                validSummaryJson().replace("\"sourceChapterId\":\"chapter_1\"", "\"sourceChapterId\":\"chapter_2\"")
        )));

        assertThatThrownBy(() -> stage.summarize(chapter()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 章节摘要来源章节不一致");
    }

    @Test
    void summarizeRejectsSummaryWithoutKeyEvents() {
        LlmChapterSummaryStage stage = stage(RecordingLlmService.responding(ChatResponse.text("""
                {
                  "chapterId":"chapter_1",
                  "chapterIndex":1,
                  "title":"第一章 雨夜",
                  "summary":"林夜在雨夜破庙中改变任务。",
                  "keyEvents":[],
                  "characters":["林夜"],
                  "locations":["破庙"],
                  "timeExpressions":["雨夜"],
                  "conflicts":["刺杀任务与救人选择冲突"],
                  "innerStates":[],
                  "sourceEvidence":[{"sourceChapterId":"chapter_1","chapterIndex":1,"excerpt":"林夜听见孩子哭声"}]
                }
                """)));

        assertThatThrownBy(() -> stage.summarize(chapter()))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI 章节摘要缺少关键事件");
    }

    private LlmChapterSummaryStage stage(RecordingLlmService llmService) {
        return new LlmChapterSummaryStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                objectMapper
        );
    }

    private NovelChapter chapter() {
        return new NovelChapter(
                "chapter_1",
                1,
                "第一章 雨夜",
                "林夜听见孩子哭声，握刀的手微微停住。他意识到这场刺杀另有隐情。",
                36
        );
    }

    private String validSummaryJson() {
        return """
                {
                  "chapterId":"chapter_1",
                  "chapterIndex":1,
                  "title":"第一章 雨夜",
                  "summary":"林夜在雨夜破庙中改变任务。",
                  "keyEvents":[
                    {
                      "eventId":"event_001",
                      "description":"林夜发现目标并非恶人。",
                      "evidence":[{"sourceChapterId":"chapter_1","chapterIndex":1,"excerpt":"林夜听见孩子哭声"}]
                    }
                  ],
                  "characters":["林夜","孩子"],
                  "locations":["破庙"],
                  "timeExpressions":["雨夜"],
                  "conflicts":["刺杀任务与救人选择冲突"],
                  "innerStates":[
                    {
                      "character":"林夜",
                      "description":"他犹豫是否继续刺杀。",
                      "evidence":{"sourceChapterId":"chapter_1","chapterIndex":1,"excerpt":"握刀的手微微停住"}
                    }
                  ],
                  "sourceEvidence":[{"sourceChapterId":"chapter_1","chapterIndex":1,"excerpt":"林夜听见孩子哭声"}]
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
