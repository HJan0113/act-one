package com.hjan.actone.script.model;

import java.time.LocalDateTime;

public record ScriptProjectListItemResponse(
        String projectId,
        String title,
        String description,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
