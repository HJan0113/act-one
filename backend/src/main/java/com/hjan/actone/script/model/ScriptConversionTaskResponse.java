package com.hjan.actone.script.model;

import java.time.LocalDateTime;

public record ScriptConversionTaskResponse(
        String taskId,
        String projectId,
        String draftId,
        String status,
        Integer progress,
        String message,
        String errorMessage,
        String failedStage,
        String errorType,
        Boolean retryable,
        String resumeStage,
        String schemaVersion,
        String modelId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
