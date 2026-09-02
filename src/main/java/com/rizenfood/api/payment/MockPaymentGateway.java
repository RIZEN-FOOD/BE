package com.rizenfood.api.payment;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 모의 결제 게이트웨이.
 *
 * ★ 실제 PG 계약 전까지 결제 흐름 전체(주문→승인→확정)를 끝까지 돌려보기 위한 임시 구현이다.
 *   요청 금액을 그대로 "승인"한 것으로 처리하고 가짜 거래번호를 발급한다.
 *
 * 포트원 등 실제 PG 가 정해지면 이 클래스를 실제 어댑터로 교체하기만 하면 된다.
 * 그때 approve() 는 PG API 를 호출하고, PG 가 알려준 실제 승인 금액을 돌려준다.
 * 호출부(OrderService)의 금액 대조 로직은 그대로 두어 위변조를 막는다.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public Approval approve(String orderNo, int expectedAmount) {
        // 실제 PG 라면 여기서 승인 API 를 호출하고 실패 시 PaymentException 을 던진다.
        String tid = "MOCK-" + Math.abs(random.nextLong());
        return new Approval(tid, expectedAmount, "간편결제(모의)", null);
    }
}
