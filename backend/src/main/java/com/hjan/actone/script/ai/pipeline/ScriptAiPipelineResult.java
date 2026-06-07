package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.service.ScriptYamlValidationResult;

public record ScriptAiPipelineResult(
        String schemaVersion,
        String title,
        int chapterCount,
        String yaml,
        ScriptAiGenerationMode generationMode,
        boolean fallback,
        String fallbackReason,
        ScriptYamlValidationResult validationResult,
        PipelineContext context
) {

    public boolean valid() {
        return validationResult != null && validationResult.valid();
    }
}
