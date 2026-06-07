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
import com.hjan.actone.script.ai.model.PipelineContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class LlmScriptYamlGenerationStage implements ScriptYamlGenerationStage {

    private static final double YAML_TEMPERATURE = 0.15D;
    private static final double YAML_TOP_P = 0.8D;
    private static final int YAML_MAX_TOKENS = 8192;
    private static final Duration YAML_TIMEOUT = Duration.ofSeconds(120);
    private static final Pattern LEADING_CODE_FENCE =
            Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE =
            Pattern.compile("\\n?```[\\t ]*$");
    private static final String SCHEMA_SUMMARY = """
            顶层必须包含 schema_version、kind、metadata、source、characters、acts、scenes。
            schema_version 固定为 "1.0"，kind 固定为 "screenplay_draft"。
            source.type 固定为 "novel"，source.generation_mode 固定为 "ai_pipeline"。
            scenes 每项必须包含 id、act_id、chapter_index、source_chapter_id、title、location、time、scene_purpose、action、dialogues、source_excerpt。
            dialogues 每项应包含 speaker、line，可包含 emotion。
            """;

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public LlmScriptYamlGenerationStage(LLMService llmService,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public String generate(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.globalStoryState().characters().isEmpty()) {
            throw new IllegalArgumentException("globalStoryState 不能为空");
        }
        if (context.scenePlan() == null || context.scenePlan().scenes().isEmpty()) {
            throw new IllegalArgumentException("scenePlan 不能为空");
        }
        if (context.cinematicTreatment() == null || context.cinematicTreatment().scenes().isEmpty()) {
            throw new IllegalArgumentException("cinematicTreatment 不能为空");
        }
        if (context.dialogueDraft() == null || context.dialogueDraft().scenes().isEmpty()) {
            throw new IllegalArgumentException("dialogueDraft 不能为空");
        }
        if (context.sourceTrace() == null || context.sourceTrace().scenes().isEmpty()) {
            throw new IllegalArgumentException("sourceTrace 不能为空");
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.SCRIPT_YAML_GENERATION,
                Map.of(
                        "project_title", context.projectTitle(),
                        "author", optionalValue(context.author()),
                        "logline", optionalValue(context.logline()),
                        "chapter_count", context.chapterCount(),
                        "global_story_state", serialize(context.globalStoryState(), "全局故事状态序列化失败"),
                        "scene_plans", serialize(context.scenePlan(), "场景规划序列化失败"),
                        "cinematic_treatment", serialize(context.cinematicTreatment(), "心理影视化结果序列化失败"),
                        "dialogue_draft", serialize(context.dialogueDraft(), "剧本对白序列化失败"),
                        "source_trace", serialize(context.sourceTrace(), "源文追溯序列化失败"),
                        "schema_summary", SCHEMA_SUMMARY
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                YAML_TEMPERATURE,
                YAML_TOP_P,
                YAML_MAX_TOKENS,
                YAML_TIMEOUT,
                ChatResponseFormat.TEXT
        ));
        return cleanYaml(response);
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private String cleanYaml(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI 剧本 YAML 返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        String cleaned = response.content()
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.strip();
        if (!hasText(cleaned)) {
            throw new RemoteException("AI 剧本 YAML 返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        return cleaned;
    }

    private String optionalValue(String value) {
        return hasText(value) ? value : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
