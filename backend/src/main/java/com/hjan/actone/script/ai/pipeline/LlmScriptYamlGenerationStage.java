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
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class LlmScriptYamlGenerationStage implements ScriptYamlGenerationStage {

    private static final double YAML_TEMPERATURE = 0.15D;
    private static final double YAML_TOP_P = 0.8D;
    private static final int YAML_MAX_TOKENS = 8192;
    private static final Duration DEFAULT_YAML_TIMEOUT = Duration.ofSeconds(120);
    private static final Pattern LEADING_CODE_FENCE =
            Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE =
            Pattern.compile("\\n?```[\\t ]*$");
    private static final String SCHEMA_SUMMARY = """
            顶层必须包含 schema_version、kind、metadata、source、characters、acts、scenes。
            schema_version 固定为 "1.0"，kind 固定为 "screenplay_draft"。
            source 必须包含 type（固定 "novel"）、generation_mode（固定 "ai_pipeline"）、chapter_count（正整数，与项目章节数一致）。
            acts 每项必须包含 id、title、chapter_range（对象，含 start 和 end 正整数，start <= end）。
            scenes 每项必须包含 id、act_id、chapter_index、source_chapter_id、title、location、time、scene_purpose、action、dialogues、source_excerpt、ai_expansion。
            act_id 必须引用 acts 中已定义的 id；chapter_index 不能超过 source.chapter_count。
            dialogues 每项应包含 speaker、line，可包含 emotion。
            characters 需要从 chapter_summaries 与 scene_plan 中归纳人物名或稳定人物标识。
            action 需要把心理、情绪和抽象叙述转译为可拍动作、表情、道具、沉默或转场。
            dialogues 需要基于场景冲突生成可编辑对白，避免角色口吻趋同。
            ai_expansion 为 boolean；当 action 或 dialogues 含源文摘录外的影视化、压缩、转场或对白改写时必须为 true。
            """;

    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public LlmScriptYamlGenerationStage(LLMService llmService,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper) {
        this(llmService, promptTemplateService, objectMapper, DEFAULT_YAML_TIMEOUT);
    }

    public LlmScriptYamlGenerationStage(LLMService llmService,
                                        PromptTemplateService promptTemplateService,
                                        ObjectMapper objectMapper,
                                        Duration timeout) {
        this.llmService = Objects.requireNonNull(llmService, "LLMService 不能为空");
        this.promptTemplateService = Objects.requireNonNull(promptTemplateService, "PromptTemplateService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.timeout = ScriptAiTimeouts.requirePositive(timeout, DEFAULT_YAML_TIMEOUT, "YAML 生成 timeout");
    }

    @Override
    public String generate(PipelineContext context) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.chapterSummaries().isEmpty()) {
            throw new IllegalArgumentException("chapterSummaries 不能为空");
        }
        if (context.scenePlan() == null || context.scenePlan().scenes().isEmpty()) {
            throw new IllegalArgumentException("scenePlan 不能为空");
        }

        long startedNanos = System.nanoTime();
        log.info("开始YAML生成 taskId={} 场景数={}", context.taskId(), context.scenePlan().scenes().size());
        RenderedPrompt prompt = promptTemplateService.render(
                PromptTemplateId.SCRIPT_YAML_GENERATION,
                Map.of(
                        "project_title", context.projectTitle(),
                        "author", optionalValue(context.author()),
                        "logline", optionalValue(context.logline()),
                        "chapter_count", context.chapterCount(),
                        "chapter_summaries", serialize(context.chapterSummaries(), "章节摘要序列化失败"),
                        "scene_plan", serialize(context.scenePlan(), "场景规划序列化失败"),
                        "schema_summary", SCHEMA_SUMMARY
                )
        );
        ChatResponse response = llmService.chat(new ChatRequest(
                List.of(ChatMessage.user(prompt.content())),
                null,
                YAML_TEMPERATURE,
                YAML_TOP_P,
                YAML_MAX_TOKENS,
                timeout,
                ChatResponseFormat.TEXT
        ));
        String yaml = cleanYaml(response);
        log.info("完成YAML生成 taskId={} 耗时={}ms YAML长度={} 响应模型={}",
                context.taskId(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                yaml.length(),
                response.model());
        return yaml;
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
