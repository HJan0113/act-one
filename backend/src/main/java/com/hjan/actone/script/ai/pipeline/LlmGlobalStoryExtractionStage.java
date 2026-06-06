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
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LlmGlobalStoryExtractionStage implements GlobalStoryExtractionStage {

    private static final double EXTRACTION_TEMPERATURE = 0.2D;
    private static final double EXTRACTION_TOP_P = 0.8D;
    private static final int EXTRACTION_MAX_TOKENS = 4096;
    private static final Duration EXTRACTION_TIMEOUT = Duration.ofSeconds(90);

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final GlobalStoryStateMerger globalStoryStateMerger;

    public LlmGlobalStoryExtractionStage(LLMService llmService,
                                         PromptTemplateService promptTemplateService,
                                         ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, new GlobalStoryStateMerger());
    }

    public LlmGlobalStoryExtractionStage(LLMService llmService,
                                         PromptTemplateService promptTemplateService,
                                         ObjectMapper objectMapper,
                                         GlobalStoryStateMerger globalStoryStateMerger) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.globalStoryStateMerger = Objects.requireNonNull(globalStoryStateMerger, "GlobalStoryStateMerger 不能为空");
    }

    @Override
    public GlobalStoryState extract(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.chapterSummaries().isEmpty()) {
            throw new IllegalArgumentException("chapterSummaries 不能为空");
        }
        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.GLOBAL_STORY_EXTRACTION,
                Map.of(
                        "chapter_summaries", serialize(context.chapterSummaries(), "章节摘要序列化失败"),
                        "existing_global_story_state", serialize(
                                context.globalStoryState(),
                                "已有全局故事状态序列化失败"
                        )
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                EXTRACTION_TEMPERATURE,
                EXTRACTION_TOP_P,
                EXTRACTION_MAX_TOKENS,
                EXTRACTION_TIMEOUT,
                ChatResponseFormat.JSON_OBJECT
        ));
        GlobalStoryState state = globalStoryStateMerger.normalize(parseState(response));
        validateState(context.chapterSummaries(), state);
        return state;
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private GlobalStoryState parseState(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 全局故事状态返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            return objectMapper.readValue(response.content(), GlobalStoryState.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new RemoteException("AI 全局故事状态结构非法", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private void validateState(List<ChapterSummary> summaries, GlobalStoryState state) {
        if (state.characters().isEmpty() || state.locations().isEmpty() || state.timeline().isEmpty()) {
            throw new RemoteException("AI 全局故事状态缺少核心事实", BaseErrorCode.REMOTE_ERROR);
        }

        Set<String> chapterIds = new HashSet<>();
        for (ChapterSummary summary : summaries) {
            chapterIds.add(summary.chapterId());
        }

        Set<String> characterIds = new HashSet<>();
        for (GlobalStoryState.CharacterProfile character : state.characters()) {
            characterIds.add(character.id());
            validateKnownChapter(chapterIds, character.firstAppearanceChapterId());
            validateSourceChapters(chapterIds, character.sourceChapterIds());
        }
        state.locations().forEach(location -> validateSourceChapters(chapterIds, location.sourceChapterIds()));
        state.timeline().forEach(event -> validateKnownChapter(chapterIds, event.chapterId()));
        state.relationships().forEach(relationship -> {
            if (!characterIds.contains(relationship.sourceCharacterId())
                    || !characterIds.contains(relationship.targetCharacterId())) {
                throw new RemoteException("AI 全局故事状态人物关系引用非法", BaseErrorCode.REMOTE_ERROR);
            }
            validateSourceChapters(chapterIds, relationship.sourceChapterIds());
        });
        state.props().forEach(prop -> validateSourceChapters(chapterIds, prop.sourceChapterIds()));
        state.foreshadowing().forEach(item -> validateSourceChapters(chapterIds, item.sourceChapterIds()));
        state.conflicts().forEach(conflict -> validateSourceChapters(chapterIds, conflict.sourceChapterIds()));
    }

    private void validateSourceChapters(Set<String> knownChapterIds, List<String> sourceChapterIds) {
        if (sourceChapterIds.isEmpty()) {
            throw new RemoteException("AI 全局故事状态缺少来源章节", BaseErrorCode.REMOTE_ERROR);
        }
        sourceChapterIds.forEach(chapterId -> validateKnownChapter(knownChapterIds, chapterId));
    }

    private void validateKnownChapter(Set<String> knownChapterIds, String chapterId) {
        if (!knownChapterIds.contains(chapterId)) {
            throw new RemoteException("AI 全局故事状态来源章节不一致", BaseErrorCode.REMOTE_ERROR);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
