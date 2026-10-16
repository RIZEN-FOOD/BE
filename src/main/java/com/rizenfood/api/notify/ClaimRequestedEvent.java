package com.rizenfood.api.notify;

/**
 * 손님이 취소·반품·교환을 요청했다 (2026-10-16). 운영자 알림 문자를 보낸다 (AdminNotifier).
 *
 * @param orderId 주문 id
 * @param type    CANCEL | RETURN | EXCHANGE
 */
public record ClaimRequestedEvent(Long orderId, String type) {
}
