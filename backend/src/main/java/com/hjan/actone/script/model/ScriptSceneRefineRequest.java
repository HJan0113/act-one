package com.hjan.actone.script.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScriptSceneRefineRequest(
        @NotBlank(message = "草稿 ID 不能为空")
        String draftId,
        @NotBlank(message = "场景 ID 不能为空")
        String sceneId,
        @NotBlank(message = "精修指令不能为空")
        @Size(max = 1000, message = "精修指令不能超过 1000 个字符")
        String instruction
) {
}
