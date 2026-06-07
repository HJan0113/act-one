package com.hjan.actone.script.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScriptYamlGenerateRequest(
        @Size(max = 128, message = "剧本标题最多 128 个字符")
        String title,
        @Size(max = 64, message = "作者最多 64 个字符")
        String author,
        @Size(max = 512, message = "一句话梗概最多 512 个字符")
        String logline,
        @NotBlank(message = "小说正文不能为空")
        String text
) {
}
