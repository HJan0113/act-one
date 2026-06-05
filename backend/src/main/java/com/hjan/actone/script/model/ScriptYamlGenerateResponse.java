package com.hjan.actone.script.model;

public record ScriptYamlGenerateResponse(
        String schemaVersion,
        String title,
        int chapterCount,
        String yaml
) {
}
