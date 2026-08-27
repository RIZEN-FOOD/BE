package com.rizenfood.api.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 누가 무엇을 언제 바꿨는지.
 *
 * 사고가 났을 때 이 기록이 유일한 단서가 된다.
 * 관리자 계정이 지워져도 로그는 남아야 하므로 이름을 값으로 박아둔다.
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_name", nullable = false, length = 100)
    private String adminName;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", nullable = false, length = 60)
    private String targetType;

    @Column(name = "target_id", length = 60)
    private String targetId;

    @Column(length = 500)
    private String summary;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AdminAuditLog() {
    }

    public AdminAuditLog(Long adminId, String adminName, String action,
                         String targetType, String targetId, String summary,
                         String ip, String userAgent) {
        this.adminId = adminId;
        this.adminName = adminName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary;
        this.ip = ip;
        this.userAgent = truncate(userAgent, 300);
        this.createdAt = Instant.now();
    }

    /** User-Agent 는 길이 제한이 없어서 잘라 담는다. */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
