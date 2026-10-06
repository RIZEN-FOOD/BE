package com.rizenfood.api.notify;

public record OrderNotificationEvent(Long orderId, Type type) {
    public enum Type{
        PAID,
        SHIPPED
    }
}
