package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.ai.model.ChatMessage;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.log.AiExceptionDiagnostics;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class ScriptAiMeteredLlmService implements LLMService {

    private static final String DEFAULT_MODEL = "default";
    private static final String UNKNOWN = "n/a";
    private final LLMService delegate;

    private ScriptAiMeteredLlmService(LLMService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "LLMService 不能为空");
    }

    public static LLMService wrap(LLMService delegate) {
        if (delegate instanceof ScriptAiMeteredLlmService) {
            return delegate;
        }
        return new ScriptAiMeteredLlmService(delegate);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "ChatRequest 不能为空");
        long startedNanos = System.nanoTime();
        logLlmRequest(request);
        try {
            ChatResponse response = delegate.chat(request);
            ScriptAiUsageTracker.record(request, response);
            logLlmResponse(request, response, startedNanos);
            return response;
        } catch (RuntimeException ex) {
            ScriptAiUsageTracker.record(request, null);
            ScriptAiLlmCallTrace.Trace trace = ScriptAiLlmCallTrace.current();
            log.warn(
                    "event=actone_ai_pipeline_llm_failure taskId={} stage={} retry={} model={} responseFormat={} durationMs={} errorType={} errorCode={} errorMessage={} modelErrorType={} modelStatus={} rootCauseType={} rootCauseMessage={}",
                    traceTaskId(trace),
                    traceStage(trace),
                    traceRetry(trace),
                    safeText(request.model(), DEFAULT_MODEL),
                    request.responseFormat(),
                    elapsedMillis(startedNanos),
                    AiExceptionDiagnostics.errorType(ex),
                    AiExceptionDiagnostics.errorCode(ex),
                    AiExceptionDiagnostics.safeMessage(ex),
                    AiExceptionDiagnostics.modelErrorType(ex),
                    AiExceptionDiagnostics.modelStatusCode(ex),
                    AiExceptionDiagnostics.rootCauseType(ex),
                    AiExceptionDiagnostics.rootCauseSafeMessage(ex)
            );
            throw ex;
        }
    }

    private void logLlmRequest(ChatRequest request) {
        ScriptAiLlmCallTrace.Trace trace = ScriptAiLlmCallTrace.current();
        log.info(
                "event=actone_ai_pipeline_llm_request taskId={} stage={} retry={} model={} responseFormat={} temperature={} topP={} maxTokens={} timeoutMs={} messageCount={} messages={}",
                traceTaskId(trace),
                traceStage(trace),
                traceRetry(trace),
                safeText(request.model(), DEFAULT_MODEL),
                request.responseFormat(),
                request.temperature(),
                request.topP(),
                request.maxTokens(),
                timeoutMillis(request.timeout()),
                request.messages().size(),
                formatMessages(request)
        );
    }

    private void logLlmResponse(ChatRequest request, ChatResponse response, long startedNanos) {
        ScriptAiLlmCallTrace.Trace trace = ScriptAiLlmCallTrace.current();
        log.info(
                "event=actone_ai_pipeline_llm_response taskId={} stage={} retry={} model={} responseModel={} responseFormat={} durationMs={} requestId={} finishReason={} promptTokens={} completionTokens={} totalTokens={} content={}",
                traceTaskId(trace),
                traceStage(trace),
                traceRetry(trace),
                safeText(request.model(), DEFAULT_MODEL),
                response == null ? null : safeText(response.model(), safeText(request.model(), DEFAULT_MODEL)),
                request.responseFormat(),
                elapsedMillis(startedNanos),
                response == null ? null : safeText(response.requestId(), "n/a"),
                response == null ? null : safeText(response.finishReason(), "n/a"),
                response == null || response.usage() == null ? null : response.usage().promptTokens(),
                response == null || response.usage() == null ? null : response.usage().completionTokens(),
                response == null || response.usage() == null ? null : response.usage().totalTokens(),
                response == null ? null : response.content()
        );
    }

    private String formatMessages(ChatRequest request) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < request.messages().size(); i++) {
            ChatMessage message = request.messages().get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("message[").append(i).append("] role=")
                    .append(message.role())
                    .append('\n')
                    .append(message.content());
        }
        return builder.toString();
    }

    private String traceTaskId(ScriptAiLlmCallTrace.Trace trace) {
        return trace == null ? UNKNOWN : safeText(trace.taskId(), UNKNOWN);
    }

    private Object traceStage(ScriptAiLlmCallTrace.Trace trace) {
        return trace == null ? UNKNOWN : trace.stage();
    }

    private String traceRetry(ScriptAiLlmCallTrace.Trace trace) {
        return trace == null ? UNKNOWN : String.valueOf(trace.retry());
    }

    private Long timeoutMillis(Duration timeout) {
        return timeout == null ? null : timeout.toMillis();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }
}
