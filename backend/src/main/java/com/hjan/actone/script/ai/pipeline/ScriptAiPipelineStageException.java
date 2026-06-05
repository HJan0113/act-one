package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineStage;

class ScriptAiPipelineStageException extends RuntimeException {

    private final PipelineStage stage;

    ScriptAiPipelineStageException(PipelineStage stage, RuntimeException cause) {
        super(cause);
        this.stage = stage;
    }

    PipelineStage stage() {
        return stage;
    }
}
