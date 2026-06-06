package com.hjan.actone.script.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ScriptDialogueVoiceRewriteRequest(
        @NotBlank(message = "草稿 ID 不能为空")
        String draftId,
        @NotBlank(message = "说话人不能为空")
        String speaker,
        @NotBlank(message = "口吻重写指令不能为空")
        @Size(max = 1000, message = "口吻重写指令不能超过 1000 个字符")
        String instruction,
        List<String> sceneIds
) {

    public ScriptDialogueVoiceRewriteRequest {
        if (sceneIds != null) {
            sceneIds = sceneIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
    }
}
