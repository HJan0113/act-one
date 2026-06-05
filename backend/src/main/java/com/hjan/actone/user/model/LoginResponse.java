package com.hjan.actone.user.model;

public record LoginResponse(
        String userId,
        String username,
        String role,
        String token,
        String avatar
) {
}

