package com.rizenfood.api.naverpay;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버페이 주문형(직가맹) 설정 (2026-10-06).
 *
 * 값은 application.yml 의 app.naverpay.* 에 있고, 키는 서버 환경변수로만 넣는다(.env 커밋 금지).
 *
 * ★ 기본은 꺼짐. 꺼져 있거나 키가 하나라도 비면 버튼·주소가 모두 숨는다(ready=false).
 *   가맹 승인 뒤 키를 넣고 mode=test 로 켜서 네이버 검수를 받고, 오픈 승인 뒤 mode=production 으로 바꾼다.
 * ★ 주소는 네이버 연동 가이드 2.1(2026-02-04)의 값이다. 버튼은 SDK v2 를 쓴다.
 *
 * @param enabled    켜기
 * @param mode       test(검수 전) | production(오픈 후)
 * @param merchantId 상점 ID. 가맹 승인 때 정해진다(np_ 로 시작)
 * @param certiKey   가맹점 인증키. 주문 정보 등록에 쓴다
 * @param buttonKey  버튼 인증키. 구매 버튼 SDK 에 쓴다(화면에 실리는 공개값)
 * @param commonKey  네이버 공통 인증키. 유입 경로 스크립트(wcslog)의 wa 값(공개값). 비우면 스크립트를 싣지 않는다
 * @param taxType    과세 종류 TAX | TAX_FREE | ZERO_TAX. 상품 과세 여부가 확정되면 맞춘다
 */
@ConfigurationProperties(prefix = "app.naverpay")
public record NaverPayProperties(
        boolean enabled,
        String mode,
        String merchantId,
        String certiKey,
        String buttonKey,
        String commonKey,
        String taxType) {

    private static final Set<String> TAX_TYPES = Set.of("TAX", "TAX_FREE", "ZERO_TAX");

    public NaverPayProperties {
        mode = "production".equalsIgnoreCase(trim(mode)) ? "production" : "test";
        merchantId = trim(merchantId);
        certiKey = trim(certiKey);
        buttonKey = trim(buttonKey);
        commonKey = trim(commonKey);
        String t = trim(taxType).toUpperCase();
        taxType = TAX_TYPES.contains(t) ? t : "TAX";
    }

    public boolean production() {
        return "production".equals(mode);
    }

    /** 켜져 있고 주문 등록·버튼에 필요한 키가 다 있는가. 하나라도 비면 아무것도 노출하지 않는다. */
    public boolean ready() {
        return enabled && !merchantId.isEmpty() && !certiKey.isEmpty() && !buttonKey.isEmpty();
    }

    /** 주문 정보 등록 주소 (가이드 표 3-1). */
    public String registerUrl() {
        return production()
                ? "https://api.pay.naver.com/o/customer/api/order/v20/register"
                : "https://test-api.pay.naver.com/o/customer/api/order/v20/register";
    }

    /** 구매 버튼 SDK v2 주소. */
    public String buttonScriptUrl() {
        return production()
                ? "https://npay-order.pstatic.net/assets/button/latest/npay.button.js"
                : "https://test-pay.naver.com/assets/button/latest/npay.button.js";
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
