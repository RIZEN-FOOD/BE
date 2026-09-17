package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 포트원 키가 아직 없을 때 (PG 계약 전 배포).
 * 서버는 떠야 하고, 결제·환불은 "결제 준비 중"으로 거절돼야 한다 — 절대 통과하면 안 된다.
 */
class PortOneWithoutKeysTest {

    private final PortOnePaymentGateway gateway = new PortOnePaymentGateway("", "https://api.portone.io");

    @Test
    @DisplayName("키가 없어도 만들어진다 (서버 기동이 막히지 않는다)")
    void startsWithoutKeys() {
        assertThat(gateway.configured()).isFalse();
    }

    @Test
    @DisplayName("결제 확인은 거절된다 — 기존 '결제 실패' 처리 경로로 흘러간다")
    void approveIsRefused() {
        assertThatThrownBy(() -> gateway.approve("R20260917-TEST000001", 12_900))
                .isInstanceOf(PaymentUnavailableException.class)
                .isInstanceOf(PaymentGateway.PaymentException.class)
                .hasMessageContaining("결제 준비 중");
    }

    @Test
    @DisplayName("환불도 거절된다 (관리자 화면에 사유가 뜨고 상태는 그대로)")
    void cancelIsRefused() {
        assertThatThrownBy(() -> gateway.cancel("R20260917-TEST000001", null, "테스트"))
                .isInstanceOf(PaymentGateway.PaymentException.class);
    }
}
