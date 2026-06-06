package com.hjan.actone.script.model;

import java.time.LocalDateTime;

public record ScriptStageResultResponse(
        String resultId,
        String taskId,
        String stage,
        String resultHash,
        Integer attempt,
        boolean reused,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
