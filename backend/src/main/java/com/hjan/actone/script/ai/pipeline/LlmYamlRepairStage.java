package com.hjan.actone.script.ai.pipeline;

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
import com.hjan.actone.script.service.ScriptYamlValidationResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class LlmYamlRepairStage implements YamlRepairStage {

    private static final double REPAIR_TEMPERATURE = 0.0D;
    private static final double REPAIR_TOP_P = 0.6D;
    private static final int REPAIR_MAX_TOKENS = 8192;
    private static final Duration DEFAULT_REPAIR_TIMEOUT = Duration.ofSeconds(120);
    private static final Pattern LEADING_CODE_FENCE =
            Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE =
            Pattern.compile("\\n?```[\\t ]*$");
    private static final String SCHEMA_SUMMARY = """
            顶层必须包含 schema_version、kind、metadata、source、characters、acts、scenes。
            schema_version 固定为 "1.0"，kind 固定为 "screenplay_draft"。
            source.type 固定为 "novel"，source.generation_mode 固定为 "ai_pipeline"。
            scenes 每项必须包含 id、act_id、chapter_index、source_chapter_id、title、location、time、scene_purpose、action、dialogues、source_excerpt、ai_expansion。
            act_id 必须引用 acts 中存在的 id；chapter_index 不得超过 source.chapter_count。
            ai_expansion 为 boolean；action 或 dialogues 超出来源证据时必须为 true，不超出时为 false。
            """;

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final Duration timeout;

    public LlmYamlRepairStage(LLMService llmService, PromptTemplateService promptTemplateService) {
        this(llmService, promptTemplateService, DEFAULT_REPAIR_TIMEOUT);
    }

    public LlmYamlRepairStage(LLMService llmService,
                              PromptTemplateService promptTemplateService,
                              Duration timeout) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_REPAIR_TIMEOUT, "YAML 修复 timeout");
    }

    @Override
    public String repair(PipelineContext context, ScriptYamlValidationResult validationResult) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        Objects.requireNonNull(validationResult, "ScriptYamlValidationResult 不能为空");
        if (validationResult.valid()) {
            throw new IllegalArgumentException("validationResult 必须为 invalid");
        }
        if (!hasText(validationResult.cleanedYaml())) {
            throw new IllegalArgumentException("invalidYaml 不能为空");
        }
        if (!hasText(validationResult.errorMessage())) {
            throw new IllegalArgumentException("validationErrors 不能为空");
        }

        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.YAML_REPAIR,
                Map.of(
                        "invalid_yaml", validationResult.cleanedYaml(),
                        "validation_errors", validationResult.errorMessage(),
                        "schema_summary", SCHEMA_SUMMARY,
                        "chapter_count", context.chapterCount()
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                REPAIR_TEMPERATURE,
                REPAIR_TOP_P,
                REPAIR_MAX_TOKENS,
                timeout,
                ChatResponseFormat.TEXT
        ));
        return cleanYaml(response);
    }

    private String cleanYaml(ChatResponse response) {
        if (response == null || !hasText(response.content())) {
            throw new RemoteException("AI YAML 修复返回为空", BaseErrorCode.REMOTE_ERROR);
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
            throw new RemoteException("AI YAML 修复返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        return cleaned;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
