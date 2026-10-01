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

    /**
     * 요청 시각. 만들 때 서버가 채운다 (2026-10-01).
     * 전에는 DB 기본값(now())에만 맡겨, 저장 직후의 객체엔 값이 없었다. 손님이 취소를 신청하면
     * 화면이 그 응답을 목록 맨 위에 붙이는데 "- 접수"로 찍혔다(새로고침해야 시각이 보였다).
     * DB 기본값은 그대로 두어, 혹시 직접 넣는 경로가 생겨도 비지 않는다. 한 번 정하면 바꾸지 않는다.
     */
    @Column(name = "requested_at", nullable = false, updatable = false)
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
        this.requestedAt = Instant.now();
    }

    /**
     * 관리자가 처리한다. 처리 시각을 남긴다.
     *
     * ★ 끝난 요청(반려·완료)은 다시 처리하지 않는다. 완료를 두 번 누르면
     *   재고가 두 번 돌아오고 환불도 두 번 시도되기 때문이다.
     */
    public void process(Status status, String adminMemo, Integer refundAmount) {
        if (!isOpen()) {
            throw new IllegalStateException("이미 처리가 끝난 요청입니다.");
        }
        if (status == Status.REQUESTED) {
            throw new IllegalArgumentException("처리 상태를 선택해 주세요.");
        }
        this.status = status.name();
        this.adminMemo = adminMemo;
        this.refundAmount = refundAmount;
        this.processedAt = Instant.now();
    }

    public boolean isRequested() {
        return Status.REQUESTED.name().equals(status);
    }

    /** 아직 끝나지 않은 요청(접수·승인). 이런 요청이 있으면 같은 주문에 새 요청을 받지 않는다. */
    public boolean isOpen() {
        return Status.REQUESTED.name().equals(status) || Status.APPROVED.name().equals(status);
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
