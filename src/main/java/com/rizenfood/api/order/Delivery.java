package com.rizenfood.api.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 배송(운송장) 정보. 주문 1건당 하나.
 * 관리자가 택배사·송장번호를 입력하면 만들어지고, 배송 상태를 추적한다.
 */
@Entity
@Table(name = "delivery")
public class Delivery {

    public enum Status { READY, SHIPPED, IN_TRANSIT, DELIVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(length = 60)
    private String carrier;

    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    @Column(nullable = false, length = 20)
    private String status = Status.READY.name();

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Delivery() {
    }

    public Delivery(Long orderId) {
        this.orderId = orderId;
    }

    public void ship(String carrier, String trackingNo) {
        this.carrier = carrier;
        this.trackingNo = trackingNo;
        this.status = Status.SHIPPED.name();
        this.shippedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markDelivered() {
        this.status = Status.DELIVERED.name();
        this.deliveredAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getCarrier() { return carrier; }
    public String getTrackingNo() { return trackingNo; }
    public String getStatus() { return status; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
