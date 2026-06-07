package com.hjan.actone.script.ai.pipeline;

public record ScriptAiPipelineOptions(
        int maxStageRetries
) {

    public ScriptAiPipelineOptions {
        if (maxStageRetries < 0) {
            throw new IllegalArgumentException("maxStageRetries 不能小于 0");
        }
    }

    public static ScriptAiPipelineOptions defaults() {
        return new ScriptAiPipelineOptions(0);
    }
}
