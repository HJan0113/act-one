package com.hjan.actone.script.ai.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.model.NovelChapter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LlmChapterSummaryStage implements ChapterSummaryStage {

    private static final double SUMMARY_TEMPERATURE = 0.2D;
    private static final double SUMMARY_TOP_P = 0.8D;
    private static final int SUMMARY_MAX_TOKENS = 2048;
    private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(60);

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public LlmChapterSummaryStage(LLMService llmService,
                                  PromptTemplateService promptTemplateService,
                                  ObjectMapper objectMapper) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public ChapterSummary summarize(NovelChapter chapter) {
        Objects.requireNonNull(chapter, "NovelChapter 不能为空");
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
                SUMMARY_TIMEOUT,
                ChatResponseFormat.JSON_OBJECT
        ));
        ChapterSummary summary = parseSummary(response);
        validateChapterReference(chapter, summary);
        return summary;
    }

    private ChapterSummary parseSummary(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 章节摘要返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            return objectMapper.readValue(response.content(), ChapterSummary.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new RemoteException("AI 章节摘要结构非法", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateChapterReference(NovelChapter chapter, ChapterSummary summary) {
        if (!chapter.id().equals(summary.chapterId()) || chapter.index() != summary.chapterIndex()) {
            throw new RemoteException("AI 章节摘要章节引用不一致", BaseErrorCode.REMOTE_ERROR);
        }
        if (summary.keyEvents().isEmpty()) {
            throw new RemoteException("AI 章节摘要缺少关键事件", BaseErrorCode.REMOTE_ERROR);
        }
        summary.sourceEvidence().forEach(evidence -> validateEvidence(chapter, evidence));
        summary.keyEvents().forEach(event -> event.evidence().forEach(evidence -> validateEvidence(chapter, evidence)));
        summary.innerStates().forEach(innerState -> validateEvidence(chapter, innerState.evidence()));
    }

    private void validateEvidence(NovelChapter chapter, SourceEvidence evidence) {
        if (!chapter.id().equals(evidence.sourceChapterId()) || chapter.index() != evidence.chapterIndex()) {
            throw new RemoteException("AI 章节摘要来源章节不一致", BaseErrorCode.REMOTE_ERROR);
        }
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
