package com.hjan.actone.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        String currentPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(max = 128, message = "新密码最多 128 个字符")
        String newPassword
) {
}

