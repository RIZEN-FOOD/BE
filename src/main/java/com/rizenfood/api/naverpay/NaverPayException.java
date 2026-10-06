package com.rizenfood.api.naverpay;

/**
 * 네이버페이 쪽 문제로 주문을 시작하지 못했다(거절·통신 실패·알 수 없는 응답).
 *
 * 메시지에는 네이버가 보낸 사유가 들어간다 — 로그에만 남기고 손님에게는 일반 문구를 보여준다.
 */
public class NaverPayException extends RuntimeException {

    public NaverPayException(String message) {
        super(message);
    }

    public NaverPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
