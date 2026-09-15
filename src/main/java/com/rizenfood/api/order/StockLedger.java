package com.rizenfood.api.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 재고 증감 이력.
 *
 * 재고 차감 자체는 product(또는 product_option)를 원자적으로 UPDATE 하고,
 * 그 결과(잔량)를 여기에 한 줄 남긴다. "왜 재고가 이 숫자가 됐는지" 추적용이다.
 * delta 음수=차감, 양수=입고.
 */
@Entity
@Table(name = "stock_ledger")
public class StockLedger {

    public enum Reason { ORDER, CANCEL, RETURN, RESTOCK, ADJUST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_option_id")
    private Long productOptionId;

    @Column(nullable = false)
    private int delta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 300)
    private String memo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected StockLedger() {
    }

    public StockLedger(Long productId, Long productOptionId, int delta, int balanceAfter,
                       Reason reason, Long orderId) {
        this.productId = productId;
        this.productOptionId = productOptionId;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.reason = reason.name();
        this.orderId = orderId;
    }

    public Long getId() { return id; }
}
