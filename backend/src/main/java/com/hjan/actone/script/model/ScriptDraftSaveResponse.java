package com.hjan.actone.script.model;

public record ScriptDraftSaveResponse(
        String draftId,
        String projectId,
        String title,
        String schemaVersion,
        String validationStatus
) {
}
