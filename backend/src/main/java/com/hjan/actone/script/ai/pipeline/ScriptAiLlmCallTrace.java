package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineStage;

final class ScriptAiLlmCallTrace {

    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private ScriptAiLlmCallTrace() {
    }

    static Scope open(String taskId, PipelineStage stage, int retry) {
        return bind(new Trace(taskId, stage, retry));
    }

    static Scope bind(Trace trace) {
        Trace previous = CURRENT.get();
        if (trace == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(trace);
        }
        return () -> {
            if (previous == null) {
                CURRENT.remove();
                return;
            }
            CURRENT.set(previous);
        };
    }

    static Trace current() {
        return CURRENT.get();
    }

    record Trace(String taskId, PipelineStage stage, int retry) {
    }

    @FunctionalInterface
    interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
