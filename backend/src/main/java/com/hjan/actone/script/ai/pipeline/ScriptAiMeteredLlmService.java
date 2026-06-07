package com.hjan.actone.script.ai.pipeline;

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
            log.warn("LLM调用失败 taskId={} stage={} retry={} 模型={} 响应格式={} 耗时={}ms 错误类型={} 错误码={} 错误信息={} 模型错误类型={} 模型状态={} 根因类型={} 根因信息={}",
                    traceTaskId(trace), traceStage(trace), traceRetry(trace),
                    safeText(request.model(), DEFAULT_MODEL), request.responseFormat(),
                    elapsedMillis(startedNanos),
                    AiExceptionDiagnostics.errorType(ex), AiExceptionDiagnostics.errorCode(ex),
                    AiExceptionDiagnostics.safeMessage(ex), AiExceptionDiagnostics.modelErrorType(ex),
                    AiExceptionDiagnostics.modelStatusCode(ex), AiExceptionDiagnostics.rootCauseType(ex),
                    AiExceptionDiagnostics.rootCauseSafeMessage(ex));
            throw ex;
        }
    }

    private void logLlmRequest(ChatRequest request) {
        ScriptAiLlmCallTrace.Trace trace = ScriptAiLlmCallTrace.current();
        log.info("LLM请求 taskId={} stage={} retry={} 模型={} 响应格式={} 温度={} topP={} 最大token数={} 超时={}ms 消息数={}",
                traceTaskId(trace), traceStage(trace), traceRetry(trace),
                safeText(request.model(), DEFAULT_MODEL), request.responseFormat(),
                request.temperature(), request.topP(), request.maxTokens(),
                timeoutMillis(request.timeout()), request.messages().size());
    }

    private void logLlmResponse(ChatRequest request, ChatResponse response, long startedNanos) {
        ScriptAiLlmCallTrace.Trace trace = ScriptAiLlmCallTrace.current();
        log.info("LLM响应 taskId={} stage={} retry={} 请求模型={} 响应模型={} 响应格式={} 耗时={}ms 请求ID={} 结束原因={} 提示token={} 生成token={} 总token={}",
                traceTaskId(trace), traceStage(trace), traceRetry(trace),
                safeText(request.model(), DEFAULT_MODEL),
                response == null ? null : safeText(response.model(), safeText(request.model(), DEFAULT_MODEL)),
                request.responseFormat(), elapsedMillis(startedNanos),
                response == null ? null : safeText(response.requestId(), "n/a"),
                response == null ? null : safeText(response.finishReason(), "n/a"),
                response == null || response.usage() == null ? null : response.usage().promptTokens(),
                response == null || response.usage() == null ? null : response.usage().completionTokens(),
                response == null || response.usage() == null ? null : response.usage().totalTokens());
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
