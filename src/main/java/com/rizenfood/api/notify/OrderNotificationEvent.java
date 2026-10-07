package com.rizenfood.api.notify;

/**
 * 알림톡을 보낼 주문 사건.
 *
 * @param amount 환불 알림의 환불 금액. 다른 알림은 null 이다(주문에서 읽는다)
 */
public record OrderNotificationEvent(Long orderId, Type type, Integer amount) {
    public enum Type{
        PAID,
        SHIPPED,
        /** 취소·반품 승인으로 PG 환불까지 끝났을 때 (2026-10-07). */
        REFUNDED
    }

    public OrderNotificationEvent(Long orderId, Type type) {
        this(orderId, type, null);
    }
}
