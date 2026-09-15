package com.rizenfood.api.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 취소·반품·교환 요청(청약철회 이력).
 *
 * ★ 전자상거래법상 청약철회 이력이다 (CLAUDE.md §7). 분쟁의 근거가 되므로
 *   요청 시각(requestedAt)과 처리 시각(processedAt)을 반드시 남긴다.
 */
@Entity
@Table(name = "order_claim")
public class OrderClaim {

    public enum Type { CANCEL, RETURN, EXCHANGE }

    public enum Status { REQUESTED, APPROVED, REJECTED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "reason_text", length = 1000)
    private String reasonText;

    @Column(nullable = false, length = 20)
    private String status = Status.REQUESTED.name();

    @Column(name = "refund_amount")
    private Integer refundAmount;

    @Column(name = "admin_memo", length = 1000)
    private String adminMemo;

    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OrderClaim() {
    }

    public OrderClaim(Long orderId, Type type, String reasonCode, String reasonText) {
        this.orderId = orderId;
        this.type = type.name();
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
    }

    /** 관리자가 처리한다. 처리 시각을 남긴다. */
    public void process(Status status, String adminMemo, Integer refundAmount) {
        this.status = status.name();
        this.adminMemo = adminMemo;
        this.refundAmount = refundAmount;
        this.processedAt = Instant.now();
    }

    public boolean isRequested() {
        return Status.REQUESTED.name().equals(status);
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getType() { return type; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonText() { return reasonText; }
    public String getStatus() { return status; }
    public Integer getRefundAmount() { return refundAmount; }
    public String getAdminMemo() { return adminMemo; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
