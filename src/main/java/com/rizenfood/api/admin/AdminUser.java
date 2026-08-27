package com.rizenfood.api.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 관리자 계정.
 *
 * 비밀번호는 BCrypt 해시만 저장한다. 평문·MD5·SHA 금지 (기획서 §6.2).
 * 로그인 5회 실패 시 10분 잠금.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    /** 로그인 실패 허용 횟수 */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    /** 잠금 시간 */
    public static final int LOCK_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AdminUser() {
    }

    public AdminUser(String username, String passwordHash, String displayName, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
        this.failedCount = 0;
    }

    /** 지금 잠겨 있는가 */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** 잠금이 풀릴 때까지 남은 분 (올림) */
    public long lockRemainingMinutes() {
        if (!isLocked()) {
            return 0;
        }
        long seconds = lockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
        return (seconds + 59) / 60;
    }

    /**
     * 로그인 실패를 기록한다. 허용 횟수를 넘기면 잠근다.
     *
     * 잠금은 계정 기준이다. IP 기준 제한은 게이트웨이·WAF 층에서 함께 건다
     * (기획서 §6.2 는 IP + 계정 두 기준을 요구한다).
     */
    public void recordFailure() {
        this.failedCount += 1;
        if (this.failedCount >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCK_MINUTES * 60L);
            this.failedCount = 0;
        }
        this.updatedAt = Instant.now();
    }

    /** 로그인 성공. 실패 누적과 잠금을 지운다. */
    public void recordSuccess() {
        this.failedCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
