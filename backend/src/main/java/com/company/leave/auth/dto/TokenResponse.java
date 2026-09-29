package com.company.leave.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    public static TokenResponse bearer(String access, String refresh, long expiresIn) {
        return new TokenResponse(access, refresh, "Bearer", expiresIn);
    }
}
