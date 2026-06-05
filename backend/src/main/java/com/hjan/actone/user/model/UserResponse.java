package com.hjan.actone.user.model;

import java.time.LocalDateTime;

public record UserResponse(
        String id,
        String username,
        String role,
        String avatar,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}

