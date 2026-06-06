package com.hjan.actone.script.ai.pipeline;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.support.FakeScriptAiStages;
import com.hjan.actone.script.ai.support.ScriptAiTestFixtures;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAiPipelineLoggingTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    @Test
    void successfulPipelineLogsStageTimingAndValidationStatusWithoutSourceText() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        ScriptAiPipelineResult result;
        try {
            result = service(new FakeScriptAiStages()).generate(ScriptAiTestFixtures.sampleRequest());
        } finally {
            detachLogAppender(appender);
        }

        String logs = formattedLogs(appender);
        assertThat(result.context().taskId()).startsWith("task_");
        assertThat(logs)
                .contains("event=actone_ai_pipeline_start")
                .contains("event=actone_ai_stage_start")
                .contains("event=actone_ai_stage_success")
                .contains("event=actone_ai_yaml_validation_result")
                .contains("event=actone_ai_pipeline_complete")
                .contains("taskId=" + result.context().taskId())
                .contains("stage=CHAPTER_SUMMARY")
                .contains("stage=YAML_VALIDATION")
                .contains("model=n/a")
                .contains("retry=0")
                .contains("durationMs=")
                .contains("validationStatus=valid")
                .contains("generationMode=ai_pipeline")
                .doesNotContain("夜雨孤庙", "林夜缓缓睁开眼", "破庙伏杀", "api-key");
    }

    @Test
    void failedStageLogsFailureAndFallbackWithoutProviderMessage() {
        FakeScriptAiStages stages = new FakeScriptAiStages()
                .failingAt(
                        PipelineStage.SCRIPT_YAML_GENERATION,
                        new RemoteException(
                                "Read timed out, api-key=secret, body=完整小说正文",
                                BaseErrorCode.SERVICE_TIMEOUT_ERROR
                        )
                );
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        ScriptAiPipelineResult result;
        try {
            result = service(stages).generate(ScriptAiTestFixtures.sampleRequest());
        } finally {
            detachLogAppender(appender);
        }

        String logs = formattedLogs(appender);
        assertThat(result.fallback()).isTrue();
        assertThat(logs)
                .contains("event=actone_ai_stage_failure")
                .contains("event=actone_ai_pipeline_fallback")
                .contains("event=actone_ai_pipeline_fallback_success")
                .contains("event=actone_ai_pipeline_complete")
                .contains("taskId=" + result.context().taskId())
                .contains("stage=SCRIPT_YAML_GENERATION")
                .contains("errorType=RemoteException")
                .contains("generationMode=rule_based_fallback")
                .doesNotContain("Read timed out", "api-key", "secret", "完整小说正文", "破庙伏杀");
    }

    private ScriptAiPipelineService service(FakeScriptAiStages stages) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                stages,
                stages,
                stages,
                stages,
                stages,
                stages
        );
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ScriptAiPipelineService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ScriptAiPipelineService.class);
        logger.detachAppender(appender);
    }

    private String formattedLogs(ListAppender<ILoggingEvent> appender) {
        return String.join(
                "\n",
                appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .toList()
        );
    }
}
