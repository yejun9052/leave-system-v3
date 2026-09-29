package com.company.leave.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/검증. access/refresh 토큰을 모두 서명 대칭키(HS256)로 처리한다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessValidity;
    private final Duration refreshValidity;

    public JwtTokenProvider(JwtProperties props) {
        String secret = props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT 서명키(JWT_SECRET)가 미설정이거나 너무 짧습니다. "
                    + "운영 환경변수 JWT_SECRET 에 32바이트(권장 48바이트) 이상의 무작위 값을 설정하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessValidity = Duration.ofMinutes(props.accessTokenValidityMinutes());
        this.refreshValidity = Duration.ofDays(props.refreshTokenValidityDays());
    }

    public String createAccessToken(Long employeeId) {
        return build(employeeId, TYPE_ACCESS, accessValidity);
    }

    public String createRefreshToken(Long employeeId) {
        return build(employeeId, TYPE_REFRESH, refreshValidity);
    }

    private String build(Long employeeId, String type, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(employeeId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** 토큰이 유효하면 employeeId 반환, 아니면 null. */
    public Long parseEmployeeId(String token, boolean requireRefresh) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get(CLAIM_TYPE, String.class);
            if (requireRefresh && !TYPE_REFRESH.equals(type)) {
                return null;
            }
            if (!requireRefresh && !TYPE_ACCESS.equals(type)) {
                return null;
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public long accessTokenValiditySeconds() {
        return accessValidity.toSeconds();
    }
}
