package com.rizenfood.api.inquiry;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 문의. 회원과 비회원 모두 남길 수 있다.
 *
 * 개인정보(이름·이메일·휴대폰)를 담으므로 수집 동의 시각을 반드시 남긴다
 * (consent_at NOT NULL). 휴대폰은 애플리케이션에서 암호화해 저장한다.
 *
 * purge_at 은 지금은 설정하지 않는다 — 보존기간 정책이 정해지면
 * 배치로 일괄 채우는 편이 안전하다(문의 유형별로 기간이 다를 수 있다).
 */
@Entity
@Table(name = "inquiry")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인한 회원이 남겼으면 채워진다. 비회원 문의는 null. */
    @Column(name = "member_id")
    private Long memberId;

    /** GENERAL | WHOLESALE | PARTNERSHIP | ORDER */
    @Column(nullable = false, length = 20)
    private String type = "GENERAL";

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "phone_encrypted", length = 500)
    private String phoneEncrypted;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "text")
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    /** PENDING | ANSWERED | CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "purge_at")
    private Instant purgeAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Inquiry() {
    }

    public Inquiry(Long memberId, String type, String name, String email,
                   String phoneEncrypted, String message) {
        this.memberId = memberId;
        this.type = type;
        this.name = name;
        this.email = email;
        this.phoneEncrypted = phoneEncrypted;
        this.message = message;
        this.consentAt = Instant.now();
    }

    public void answer(String answerText) {
        this.answer = answerText;
        this.answeredAt = Instant.now();
        this.status = "ANSWERED";
    }

    public void close() {
        this.status = "CLOSED";
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getType() { return type; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public String getMessage() { return message; }
    public String getAnswer() { return answer; }
    public Instant getAnsweredAt() { return answeredAt; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
