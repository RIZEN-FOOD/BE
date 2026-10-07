package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 나이스 취소 요청의 orderId (2026-10-07).
 *
 * 결제 때 쓴 주문번호를 그대로 보내면 나이스가 «이미 사용된 OrderId»로 거절한다.
 * 취소 거래마다 새 번호를 만들되, 어느 주문의 취소인지 읽히고 64자를 넘지 않아야 한다.
 */
class NicePayCancelOrderIdTest {

    @Test
    @DisplayName("취소 orderId 는 주문번호로 시작하되 주문번호와 다르고, 부를 때마다 다르다")
    void cancelOrderIdIsUniquePerCall() throws InterruptedException {
        String orderNo = "R20261007-84J4TJSR87";
        String a = NicePayGateway.cancelOrderId(orderNo);
        Thread.sleep(2);
        String b = NicePayGateway.cancelOrderId(orderNo);

        assertThat(a).startsWith(orderNo + "-C").isNotEqualTo(orderNo);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.length()).isLessThanOrEqualTo(64);
    }
}
