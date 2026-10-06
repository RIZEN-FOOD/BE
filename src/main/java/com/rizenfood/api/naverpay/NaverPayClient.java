package com.rizenfood.api.naverpay;

/**
 * 네이버페이 서버로 주문 정보 XML 을 보낸다.
 *
 * 테스트에서 실제 네이버로 나가지 않게 인터페이스로 둔다(가짜 응답을 끼운다).
 */
public interface NaverPayClient {

    /** XML 을 POST 하고 응답 본문을 그대로 돌려준다. 통신 실패는 NaverPayException. */
    String post(String url, String xml);
}
