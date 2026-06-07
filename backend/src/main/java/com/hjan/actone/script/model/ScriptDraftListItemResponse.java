package com.hjan.actone.script.model;

import java.time.LocalDateTime;

public record ScriptDraftListItemResponse(
        String draftId,
        String projectId,
        String title,
        String schemaVersion,
        String validationStatus,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
