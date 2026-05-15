package com.booksonline.auth;

public record AuthResponse(
        String token,
        AuthUser user
) {
}
