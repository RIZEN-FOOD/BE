package com.rizenfood.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 발급과 검증.
 *
 * 토큰은 HttpOnly 쿠키로만 오간다. localStorage 에 두지 않는다 (기획서 §6.2).
 * localStorage 는 XSS 한 번이면 통째로 털린다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);

        // HS256 은 256비트(32바이트) 이상을 요구한다.
        // 짧은 값이 들어오면 조용히 넘어가지 않고 시작 자체를 막는다.
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 이 너무 짧다. 32바이트 이상이어야 한다. .env 의 JWT_SECRET 을 확인하라.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String createAdminToken(Long adminId, String username, String displayName, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.adminExpiryMinutes() * 60);

        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_NAME, displayName)
                .audience().add("admin").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 내용을 꺼낸다.
     * 서명이 틀리거나 만료됐으면 빈 값을 준다. 예외를 밖으로 던지지 않는다.
     */
    public Optional<AuthenticatedAdmin> parseAdminToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    // 회원 토큰으로 관리자 API 에 들어오는 것을 막는다.
                    .requireAudience("admin")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new AuthenticatedAdmin(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_NAME, String.class),
                    claims.get(CLAIM_ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // 위조·만료·형식 오류. 어느 쪽이든 인증 실패로 같게 다룬다.
            return Optional.empty();
        }
    }

    public long adminExpirySeconds() {
        return properties.adminExpiryMinutes() * 60;
    }

    // ── 회원 토큰 ──────────────────────────────────────────────
    //  audience 를 "member" 로 두어 관리자 토큰과 섞이지 않게 한다.
    //  회원 access 토큰으로 관리자 API 에, 반대로도 들어가지 못한다.

    public String createMemberAccessToken(Long memberId, String name) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.memberAccessMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_NAME, name)
                .audience().add("member").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Optional<AuthenticatedMember> parseMemberToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireAudience("member")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedMember(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_NAME, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long memberAccessSeconds() {
        return properties.memberAccessMinutes() * 60;
    }

    public long memberRefreshSeconds() {
        return properties.memberRefreshDays() * 86400;
    }

    /** 토큰에서 꺼낸 관리자 정보 */
    public record AuthenticatedAdmin(Long id, String displayName, String role) {
    }

    /** 토큰에서 꺼낸 회원 정보 */
    public record AuthenticatedMember(Long id, String name) {
    }
}
