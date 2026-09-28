package com.fitmate.domain.auth.dto;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record Signup(Long userId) {
    }

    public record Token(String accessToken, String refreshToken, String tokenType, long expiresIn) {
        public static Token bearer(String accessToken, String refreshToken, long expiresInSeconds) {
            return new Token(accessToken, refreshToken, "Bearer", expiresInSeconds);
        }
    }
}
