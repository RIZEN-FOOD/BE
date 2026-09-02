package com.rizenfood.api.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 결제.
 *
 * ★ PG 사가 아직 확정되지 않았다(포트원 유력). 특정 업체에 종속되지 않도록
 *   pg_provider 값과 어댑터 구현만 갈아끼우는 구조다. 이 엔티티는 PG 중립이다.
 *
 * 결제 승인 시 PG 가 알려준 승인 금액과 서버가 계산한 주문 금액을 대조한 뒤에만
 * 주문을 확정한다 (CLAUDE.md 규칙 5). 그 대조는 서비스가 한다.
 */
@Entity
@Table(name = "payment")
public class Payment {

    public enum Status { READY, PAID, FAILED, CANCELLED, PARTIAL_CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "pg_provider", nullable = false, length = 40)
    private String pgProvider;

    @Column(name = "pg_tid", length = 200)
    private String pgTid;

    @Column(length = 40)
    private String method;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 20)
    private String status = Status.READY.name();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "receipt_url", length = 1000)
    private String receiptUrl;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Payment() {
    }

    public Payment(Long orderId, String pgProvider, int amount) {
        this.orderId = orderId;
        this.pgProvider = pgProvider;
        this.amount = amount;
    }

    public void markPaid(String pgTid, String method, String receiptUrl) {
        this.pgTid = pgTid;
        this.method = method;
        this.receiptUrl = receiptUrl;
        this.status = Status.PAID.name();
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED.name();
        this.failReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getPgProvider() { return pgProvider; }
    public int getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getMethod() { return method; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getReceiptUrl() { return receiptUrl; }
}
