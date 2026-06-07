package com.hjan.actone.script.model;

import java.time.LocalDateTime;

public record ScriptDraftDetailResponse(
        String draftId,
        String projectId,
        String title,
        String schemaVersion,
        String yaml,
        String validationStatus,
        String validationError,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
