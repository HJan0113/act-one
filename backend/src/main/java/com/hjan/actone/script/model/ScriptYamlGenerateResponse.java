package com.hjan.actone.script.model;

public record ScriptYamlGenerateResponse(
        String schemaVersion,
        String title,
        int chapterCount,
        String yaml,
        String generationMode,
        boolean fallback,
        String fallbackReason,
        String validationStatus,
        String validationError
) {
}
