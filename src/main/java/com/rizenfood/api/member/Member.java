package com.rizenfood.api.member;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원.
 *
 * 개인정보를 담으므로 다루는 규칙이 엄격하다 (기획서 §6·§10).
 *  - 비밀번호는 BCrypt 해시만 저장한다. 소셜 가입은 비밀번호가 없다.
 *  - 휴대폰 번호는 애플리케이션에서 암호화해 저장한다 (phone_encrypted).
 *  - 동의 시각(약관·개인정보·마케팅)을 각각 기록한다.
 *  - 로그인 5회 실패 시 10분 잠근다.
 *  - 탈퇴 시 개인정보를 파기하고 purge_at 을 설정한다.
 */
@Entity
@Table(name = "member")
public class Member {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String email;

    /** 소셜 회원은 null. BCrypt 해시만 저장한다. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    /** 애플리케이션에서 암호화해 저장한다. 평문으로 넣지 않는다. */
    @Column(name = "phone_encrypted", length = 500)
    private String phoneEncrypted;

    /** LOCAL | KAKAO | NAVER */
    @Column(nullable = false, length = 20)
    private String provider = "LOCAL";

    @Column(name = "provider_id", length = 200)
    private String providerId;

    /** ACTIVE | DORMANT | WITHDRAWN */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private Instant privacyAgreedAt;

    @Column(name = "marketing_agreed_at")
    private Instant marketingAgreedAt;

    @Column(name = "age_verified_at")
    private Instant ageVerifiedAt;

    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "purge_at")
    private Instant purgeAt;

    protected Member() {
    }

    /** 로컬(이메일) 가입 회원 생성 */
    public static Member localMember(String email, String passwordHash, String name) {
        Member m = new Member();
        m.email = email;
        m.passwordHash = passwordHash;
        m.name = name;
        m.provider = "LOCAL";
        m.status = "ACTIVE";
        return m;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public long lockRemainingMinutes() {
        if (!isLocked()) {
            return 0;
        }
        long seconds = lockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
        return (seconds + 59) / 60;
    }

    public boolean isWithdrawn() {
        return "WITHDRAWN".equals(status);
    }

    public void recordLoginFailure() {
        this.failedCount += 1;
        if (this.failedCount >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCK_MINUTES * 60L);
            this.failedCount = 0;
        }
        this.updatedAt = Instant.now();
    }

    public void recordLoginSuccess() {
        this.failedCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** 동의 시각을 기록한다. 마케팅은 선택이라 null 일 수 있다. */
    public void recordConsents(boolean marketing, boolean ageVerified) {
        Instant now = Instant.now();
        this.termsAgreedAt = now;
        this.privacyAgreedAt = now;
        this.marketingAgreedAt = marketing ? now : null;
        this.ageVerifiedAt = ageVerified ? now : null;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.updatedAt = Instant.now();
    }

    public void updatePhone(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
        this.updatedAt = Instant.now();
    }

    /**
     * 탈퇴. 개인정보를 지우고 상태를 바꾼다.
     * 이메일·이름·휴대폰을 파기하고, 법정 보존기간이 지나면 완전 삭제되도록 purge_at 을 둔다.
     */
    public void withdraw(int retentionDays) {
        this.status = "WITHDRAWN";
        this.withdrawnAt = Instant.now();
        this.purgeAt = Instant.now().plusSeconds(retentionDays * 86400L);
        // 즉시 파기: 다시 로그인할 수 없도록 비밀번호와 식별정보를 지운다.
        // 이메일은 재가입 판별을 위해 해시성 값으로 대체하지 않고, 여기서는 마스킹한다.
        this.passwordHash = null;
        this.phoneEncrypted = null;
        this.name = "탈퇴한 회원";
        this.email = "withdrawn+" + this.id + "@rizen.invalid";
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public String getProvider() { return provider; }
    public String getStatus() { return status; }
    public int getFailedCount() { return failedCount; }
    public Instant getMarketingAgreedAt() { return marketingAgreedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
