package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineStage;

public class ScriptAiPipelineCanceledException extends RuntimeException {

    private final PipelineStage stage;

    public ScriptAiPipelineCanceledException(PipelineStage stage) {
        super("AI Pipeline 已取消：" + stage);
        this.stage = stage;
    }

    public PipelineStage stage() {
        return stage;
    }
}
