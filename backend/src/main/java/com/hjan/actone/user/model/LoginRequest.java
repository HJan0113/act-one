package com.hjan.actone.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名最多 64 个字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(max = 128, message = "密码最多 128 个字符")
        String password
) {
}

