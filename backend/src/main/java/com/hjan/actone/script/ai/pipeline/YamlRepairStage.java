package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.service.ScriptYamlValidationResult;

public interface YamlRepairStage {

    String repair(PipelineContext context, ScriptYamlValidationResult validationResult);
}
