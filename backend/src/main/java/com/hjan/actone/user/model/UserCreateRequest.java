package com.hjan.actone.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名最多 64 个字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(max = 128, message = "密码最多 128 个字符")
        String password,
        @Size(max = 32, message = "角色最多 32 个字符")
        String role,
        @Size(max = 512, message = "头像地址最多 512 个字符")
        String avatar
) {
}

