package com.rizenfood.api.payment;

import java.security.SecureRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 모의 결제 게이트웨이.
 *
 * 개발·시연용. 결제 흐름 전체(주문→승인→확정→취소)를 PG 없이 끝까지 돌려볼 수 있다.
 * 요청 금액을 그대로 "승인"한 것으로 처리하고 가짜 거래번호를 발급한다.
 *
 * app.payment.provider 가 mock 이거나 비어 있을 때 쓰인다(기본값).
 * 실제 결제는 PAYMENT_PROVIDER=portone 으로 바꾸면 PortOnePaymentGateway 가 대신한다.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public Approval approve(String orderNo, int expectedAmount) {
        String tid = "MOCK-" + Math.abs(random.nextLong());
        return new Approval(tid, expectedAmount, "테스트 결제(모의)", null);
    }

    @Override
    public void cancel(String orderNo, Integer amount, String reason) {
        // 모의 결제는 되돌릴 실제 돈이 없다. 상태 정리는 호출부가 한다.
    }
}
