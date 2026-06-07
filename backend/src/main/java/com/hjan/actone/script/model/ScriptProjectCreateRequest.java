package com.hjan.actone.script.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScriptProjectCreateRequest(
        @Size(max = 128, message = "项目标题最多 128 个字符")
        String title,
        @Size(max = 512, message = "项目描述最多 512 个字符")
        String description,
        @NotBlank(message = "小说正文不能为空")
        String text
) {
}

