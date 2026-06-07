package com.hjan.actone.user.model;

public record CurrentUserResponse(
        String userId,
        String username,
        String role,
        String avatar
) {
}

