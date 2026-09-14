package com.rizenfood.api.payment;

/**
 * 결제 게이트웨이(PG) 어댑터.
 *
 * 결제 연동부를 이 인터페이스 뒤에 숨긴다. 구현체는 설정(app.payment.provider)으로 고른다.
 *   - mock    : MockPaymentGateway    (개발·시연용, 요청 금액을 그대로 승인)
 *   - portone : PortOnePaymentGateway (포트원 V2, 실제 결제)
 * 서비스 코드(OrderService·ClaimService)는 어느 PG 든 그대로다.
 *
 * approve() 는 결제 결과를 PG 에서 확인해 돌려준다.
 * 반환된 승인 금액(approvedAmount)은 서버가 계산한 주문 금액과 반드시 대조된다.
 * 대조는 호출부(OrderService)가 하며, 어긋나면 주문을 확정하지 않는다.
 */
public interface PaymentGateway {

    /** pg_provider 컬럼에 저장될 식별자. */
    String provider();

    /**
     * 결제 승인(확인).
     *
     * @param orderNo        주문번호 (= PG 결제 ID)
     * @param expectedAmount 서버가 계산한 결제 요청 금액
     * @return 승인 결과(거래번호·실제 승인 금액·수단·영수증)
     * @throws PaymentException 결제되지 않았거나 확인 실패
     */
    Approval approve(String orderNo, int expectedAmount);

    /**
     * 결제 취소(환불).
     *
     * @param orderNo 주문번호 (= PG 결제 ID)
     * @param amount  환불 금액. null 이면 전액
     * @param reason  취소 사유 (PG·영수증에 남는다)
     * @throws PaymentException PG 가 취소를 거절했거나 요청 실패
     */
    void cancel(String orderNo, Integer amount, String reason);

    /** 승인 결과. */
    record Approval(String tid, int approvedAmount, String method, String receiptUrl) {
    }

    /** 승인·취소 실패. */
    class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }
    }
}
