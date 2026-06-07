package com.hjan.actone.common.context;

public record LoginUser(
        String userId,
        String username,
        String role,
        String avatar
) {
}

