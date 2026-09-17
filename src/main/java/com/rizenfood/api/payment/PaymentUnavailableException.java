package com.rizenfood.api.payment;

/**
 * 결제를 받을 수 없는 상태 (PG 키가 아직 설정되지 않음 등).
 *
 * 결제를 "성공한 것처럼" 넘기지 않고 명확히 거절하기 위한 예외다.
 *
 * ★ PaymentException 의 한 종류로 둔다. 결제 확인·방치 주문 정리 쪽이 이미 PaymentException 을
 *   "결제 안 됨"으로 처리하므로, 키가 없을 때도 주문이 결제된 것으로 넘어가지 않고
 *   방치 주문의 재고도 정상적으로 풀린다.
 */
public class PaymentUnavailableException extends PaymentGateway.PaymentException {

    public PaymentUnavailableException(String message) {
        super(message);
    }
}
