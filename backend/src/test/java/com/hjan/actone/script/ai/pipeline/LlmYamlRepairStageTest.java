package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.model.ChatResponseFormat;
import com.hjan.actone.ai.model.ChatRole;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.prompt.ClasspathPromptTemplateService;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LlmYamlRepairStageTest {

    private final ScriptYamlValidator validator = new ScriptYamlValidator();

    @Test
    void repairRendersValidationErrorsAndReturnsCleanYaml() {
        ScriptYamlValidationResult invalid = validator.validate(invalidYaml());
        RecordingLlmService llmService = RecordingLlmService.responding(ChatResponse.text("""
                ```yaml
                %s
                ```
                """.formatted(validYaml())));
        LlmYamlRepairStage stage = stage(llmService);

        String repaired = stage.repair(context(), invalid);

        assertThat(repaired)
                .startsWith("schema_version: \"1.0\"")
                .contains("generation_mode: \"ai_pipeline\"")
                .doesNotContain("```");
        assertThat(validator.validate(repaired).valid()).isTrue();

        ChatRequest request = llmService.requests().get(0);
        assertThat(request.responseFormat()).isEqualTo(ChatResponseFormat.TEXT);
        assertThat(request.model()).isNull();
        assertThat(request.temperature()).isEqualTo(0.0D);
        assertThat(request.topP()).isEqualTo(0.6D);
        assertThat(request.maxTokens()).isEqualTo(8192);
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(request.messages()).singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(ChatRole.USER);
                    assertThat(message.content())
                            .contains("<validation_errors>")
                            .contains("<schema_summary>")
                            .contains("<invalid_yaml>")
                            .contains("acts 至少需要 1 个幕")
                            .contains("ai_expansion")
                            .contains("chapter_count")
                            .contains("generation_mode: \"ai_pipeline\"")
                            .doesNotContain("{{validation_errors}}", "{{schema_summary}}", "{{invalid_yaml}}");
                });
    }

    @Test
    void repairRejectsBlankModelResponse() {
        LlmYamlRepairStage stage = stage(RecordingLlmService.responding(ChatResponse.text("  ")));

        assertThatThrownBy(() -> stage.repair(context(), validator.validate(invalidYaml())))
                .isInstanceOf(RemoteException.class)
                .hasMessage("AI YAML 修复返回为空");
    }

    @Test
    void repairPropagatesRemoteFailureFromLlmService() {
        RemoteException timeout = new RemoteException(
                "AI 模型调用超时，请稍后重试",
                BaseErrorCode.SERVICE_TIMEOUT_ERROR
        );
        LlmYamlRepairStage stage = stage(RecordingLlmService.failing(timeout));

        Throwable actual = catchThrowable(() -> stage.repair(context(), validator.validate(invalidYaml())));

        assertThat(actual).isSameAs(timeout);
    }

    @Test
    void repairRejectsValidValidationResult() {
        LlmYamlRepairStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validYaml())));

        assertThatThrownBy(() -> stage.repair(context(), validator.validate(validYaml())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("validationResult 必须为 invalid");
    }

    @Test
    void repairRejectsInvalidResultWithoutYamlOrErrors() {
        LlmYamlRepairStage stage = stage(RecordingLlmService.responding(ChatResponse.text(validYaml())));

        assertThatThrownBy(() -> stage.repair(
                context(),
                new ScriptYamlValidationResult(" ", ScriptYamlValidator.VALIDATION_STATUS_INVALID, "错误")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalidYaml 不能为空");

        assertThatThrownBy(() -> stage.repair(
                context(),
                new ScriptYamlValidationResult(invalidYaml(), ScriptYamlValidator.VALIDATION_STATUS_INVALID, " ")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("validationErrors 不能为空");
    }

    private LlmYamlRepairStage stage(RecordingLlmService llmService) {
        return new LlmYamlRepairStage(
                llmService,
                new ClasspathPromptTemplateService(new DefaultResourceLoader())
        );
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "夜雨孤庙", "测试作者", "杀手林夜改变刺杀目标。", 3);
    }

    private String invalidYaml() {
        return """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "夜雨孤庙"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
                characters: []
                acts: []
                scenes: []
                """;
    }

    private String validYaml() {
        return """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "夜雨孤庙"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
                characters: []
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 3
                scenes:
                  - id: "scene_001"
                    act_id: "act_1"
                    chapter_index: 1
                    source_chapter_id: "chapter_1"
                    title: "破庙伏杀"
                    location: "荒山破庙"
                    time: "雨夜子时"
                    scene_purpose: "建立刺杀任务。"
                    action: "林夜吹熄残烛。"
                    ai_expansion: true
                    dialogues: []
                    source_excerpt: "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
                """;
    }

    private static final class RecordingLlmService implements LLMService {

        private final ChatResponse response;
        private final RuntimeException failure;
        private final List<ChatRequest> requests = new ArrayList<>();

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
            return requests;
        }
    }
}
