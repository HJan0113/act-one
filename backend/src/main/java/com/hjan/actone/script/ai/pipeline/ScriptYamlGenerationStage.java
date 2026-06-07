package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineContext;

public interface ScriptYamlGenerationStage {

    String generate(PipelineContext context);
}
