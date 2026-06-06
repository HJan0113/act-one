package com.hjan.actone.script.model;

import com.hjan.actone.script.ai.pipeline.ScriptAiUsageSummary;

public record ScriptYamlGenerateResponse(
        String schemaVersion,
        String title,
        int chapterCount,
        String yaml,
        String generationMode,
        boolean fallback,
        String fallbackReason,
        String validationStatus,
        String validationError,
        ScriptAiUsageSummary usageSummary
) {

    public ScriptYamlGenerateResponse {
        usageSummary = usageSummary == null ? ScriptAiUsageSummary.empty() : usageSummary;
    }
}
