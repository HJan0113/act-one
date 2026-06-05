package com.hjan.actone.script.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScriptDraftSaveRequest(
        @NotBlank(message = "项目 ID 不能为空")
        String projectId,
        @NotBlank(message = "草稿标题不能为空")
        @Size(max = 128, message = "草稿标题最多 128 个字符")
        String title,
        @NotBlank(message = "Schema 版本不能为空")
        @Size(max = 32, message = "Schema 版本最多 32 个字符")
        String schemaVersion,
        @NotBlank(message = "YAML 内容不能为空")
        String yaml
) {
}
