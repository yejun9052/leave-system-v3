package com.company.leave.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 관련 설정 (app.jwt.*).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidityMinutes,
        long refreshTokenValidityDays) {
}
