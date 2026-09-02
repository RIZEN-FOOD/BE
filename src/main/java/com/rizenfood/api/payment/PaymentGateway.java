package com.rizenfood.api.payment;

/**
 * 결제 게이트웨이(PG) 어댑터.
 *
 * ★ PG 사 미확정(포트원 유력). 결제 연동부를 이 인터페이스 뒤에 숨겨,
 *   업체가 정해지면 구현체(어댑터)만 갈아끼운다. 서비스 코드는 그대로다.
 *
 * approve() 는 PG 에 결제 승인을 요청하고 그 결과를 돌려준다.
 * 반환된 승인 금액(approvedAmount)은 서버가 계산한 주문 금액과 반드시 대조된다.
 * 대조는 호출부(OrderService)가 하며, 어긋나면 주문을 확정하지 않는다.
 */
public interface PaymentGateway {

    /** pg_provider 컬럼에 저장될 식별자. */
    String provider();

    /**
     * 결제 승인.
     *
     * @param orderNo        주문번호
     * @param expectedAmount 서버가 계산한 결제 요청 금액
     * @return 승인 결과(거래번호·실제 승인 금액·수단·영수증)
     * @throws PaymentException 승인 실패
     */
    Approval approve(String orderNo, int expectedAmount);

    /** 승인 결과. */
    record Approval(String tid, int approvedAmount, String method, String receiptUrl) {
    }

    /** 승인 실패. */
    class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }
    }
}
