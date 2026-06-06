package com.hjan.actone.script.ai.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
import com.hjan.actone.common.log.AiExceptionDiagnostics;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.model.NovelChapter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LlmChapterSummaryStage implements ChapterSummaryStage {

    private static final double SUMMARY_TEMPERATURE = 0.2D;
    private static final double SUMMARY_TOP_P = 0.8D;
    private static final int SUMMARY_MAX_TOKENS = 4096;
    private static final Duration DEFAULT_SUMMARY_TIMEOUT = Duration.ofSeconds(60);

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public LlmChapterSummaryStage(LLMService llmService,
                                  PromptTemplateService promptTemplateService,
                                  ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, DEFAULT_SUMMARY_TIMEOUT);
    }

    public LlmChapterSummaryStage(LLMService llmService,
                                  PromptTemplateService promptTemplateService,
                                  ObjectMapper objectMapper,
                                  Duration timeout) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_SUMMARY_TIMEOUT, "章节摘要 timeout");
    }

    @Override
    public ChapterSummary summarize(NovelChapter chapter) {
        Objects.requireNonNull(chapter, "NovelChapter 不能为空");
        long startedNanos = System.nanoTime();
        log.info("开始章节摘要 chapterId={} chapterIndex={}", chapter.id(), chapter.index());
        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.CHAPTER_SUMMARY,
                Map.of(
                        "chapter_id", requireText(chapter.id(), "chapter.id"),
                        "chapter_index", chapter.index(),
                        "chapter_title", requireText(chapter.title(), "chapter.title"),
                        "chapter_content", requireText(chapter.content(), "chapter.content")
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                SUMMARY_TEMPERATURE,
                SUMMARY_TOP_P,
                SUMMARY_MAX_TOKENS,
                timeout,
                ChatResponseFormat.JSON_OBJECT
        ));
        ChapterSummary summary = parseSummary(chapter, response);
        validateChapterReference(chapter, summary);
        log.info("完成章节摘要 chapterId={} durationMs={}", chapter.id(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        return summary;
    }

    private ChapterSummary parseSummary(NovelChapter chapter, ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            log.warn("event=actone_ai_chapter_summary_empty_response chapterId={} chapterIndex={} responsePresent={}",
                    chapter.id(), chapter.index(), response != null);
            throw new RemoteException("AI 章节摘要返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        if ("LENGTH".equalsIgnoreCase(response.finishReason())) {
            log.warn("event=actone_ai_chapter_summary_truncated chapterId={} chapterIndex={} responseLength={}",
                    chapter.id(), chapter.index(), response.content().length());
            throw new RemoteException("AI 章节摘要输出被截断", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            return objectMapper.readValue(response.content(), ChapterSummary.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.warn(
                    "event=actone_ai_chapter_summary_parse_failure chapterId={} chapterIndex={} responseLength={} errorType={} jsonPath={} safeReason={}",
                    chapter.id(),
                    chapter.index(),
                    response.content().length(),
                    ex.getClass().getSimpleName(),
                    jsonPath(ex),
                    safeValidationReason(ex)
            );
            throw new RemoteException("AI 章节摘要结构非法", ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateChapterReference(NovelChapter chapter, ChapterSummary summary) {
        if (!chapter.id().equals(summary.chapterId()) || chapter.index() != summary.chapterIndex()) {
            log.warn(
                    "event=actone_ai_chapter_summary_validation_failure reason=chapter_reference_mismatch expectedChapterId={} expectedChapterIndex={} actualChapterId={} actualChapterIndex={}",
                    chapter.id(), chapter.index(), summary.chapterId(), summary.chapterIndex());
            throw new RemoteException("AI 章节摘要章节引用不一致", BaseErrorCode.REMOTE_ERROR);
        }
        if (summary.keyEvents().isEmpty()) {
            log.warn("event=actone_ai_chapter_summary_validation_failure reason=key_events_empty chapterId={} chapterIndex={}",
                    chapter.id(), chapter.index());
            throw new RemoteException("AI 章节摘要缺少关键事件", BaseErrorCode.REMOTE_ERROR);
        }
        summary.sourceEvidence().forEach(evidence -> validateEvidence(chapter, evidence));
        summary.keyEvents().forEach(event -> event.evidence().forEach(evidence -> validateEvidence(chapter, evidence)));
        summary.innerStates().forEach(innerState -> validateEvidence(chapter, innerState.evidence()));
    }

    private void validateEvidence(NovelChapter chapter, SourceEvidence evidence) {
        if (!chapter.id().equals(evidence.sourceChapterId()) || chapter.index() != evidence.chapterIndex()) {
            log.warn(
                    "event=actone_ai_chapter_summary_validation_failure reason=source_evidence_mismatch expectedChapterId={} expectedChapterIndex={} actualChapterId={} actualChapterIndex={}",
                    chapter.id(), chapter.index(), evidence.sourceChapterId(), evidence.chapterIndex());
            throw new RemoteException("AI 章节摘要来源章节不一致", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private String jsonPath(Exception ex) {
        if (ex instanceof JsonMappingException mappingException) {
            List<String> path = mappingException.getPath().stream()
                    .map(reference -> {
                        if (hasText(reference.getFieldName())) {
                            return reference.getFieldName();
                        }
                        return reference.getIndex() >= 0 ? "[" + reference.getIndex() + "]" : null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            if (!path.isEmpty()) {
                return String.join(".", path).replace(".[", "[");
            }
        }
        return null;
    }

    private String safeValidationReason(Exception ex) {
        if (ex instanceof IllegalArgumentException) {
            return AiExceptionDiagnostics.sanitize(ex.getMessage());
        }
        if (ex.getCause() instanceof IllegalArgumentException cause) {
            return AiExceptionDiagnostics.sanitize(cause.getMessage());
        }
        return null;
    }

    private String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
