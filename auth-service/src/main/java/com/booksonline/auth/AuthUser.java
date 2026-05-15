package com.booksonline.auth;

public record AuthUser(
        Long id,
        String email,
        String fullName
) {
}
