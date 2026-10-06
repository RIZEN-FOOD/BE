package com.rizenfood.api.naverpay;

import java.util.regex.Pattern;

/**
 * 주문 정보 등록 응답 (가이드 3.1.3).
 *
 * 성공: SUCCESS:인증키:가맹점번호 — 인증키는 영문·숫자 최대 19자리.
 * 실패: FAIL:[에러코드]실패메시지
 *
 * 구매 버튼 SDK v2 는 이 두 값({key, merchantNo})을 받아 네이버페이 주문서를 직접 연다.
 */
public record NaverPayRegisterResult(String key, String merchantNo) {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9]{1,19}");
    private static final Pattern MERCHANT_NO = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    static NaverPayRegisterResult parse(String body) {
        String b = body == null ? "" : body.trim();
        if (b.startsWith("SUCCESS:")) {
            String[] parts = b.split(":", 3);
            if (parts.length == 3 && KEY.matcher(parts[1]).matches() && MERCHANT_NO.matcher(parts[2]).matches()) {
                return new NaverPayRegisterResult(parts[1], parts[2]);
            }
            throw new NaverPayException("네이버페이 응답 형식이 올바르지 않다: " + abbreviate(b));
        }
        if (b.startsWith("FAIL:")) {
            throw new NaverPayException("네이버페이 주문 등록 거절: " + abbreviate(b.substring(5).trim()));
        }
        throw new NaverPayException("네이버페이 응답을 알아볼 수 없다: " + abbreviate(b));
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
