package com.rizenfood.api.member;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 리프레시 토큰.
 *
 * Access 토큰은 짧게(30분), 이 토큰은 길게(14일) 둔다 (기획서 §6.2).
 * 원문이 아니라 해시를 저장한다. DB 가 유출돼도 토큰을 재사용할 수 없다.
 * 로그아웃하면 revoked_at 을 찍어 무효화한다.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 200)
    private String tokenHash;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(length = 64)
    private String ip;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {
    }

    public RefreshToken(Long memberId, String tokenHash, Instant expiresAt, String userAgent, String ip) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
        this.ip = ip;
        this.createdAt = Instant.now();
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Instant getExpiresAt() { return expiresAt; }
}
