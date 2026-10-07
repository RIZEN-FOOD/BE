package com.rizenfood.api.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 솔라피 발송기 — 인증 헤더·본문·응답 해석 (2026-10-07). 실제로 보내지는 않는다.
 */
class SolapiAlimtalkSenderTest {

    @Test
    @DisplayName("인증 헤더: HMAC-SHA256(시크릿, date+salt) 의 16진수 — 솔라피 SDK 와 같은 형식")
    void authorizationHeader() {
        String header = SolapiAlimtalkSender.authorization(
                "KEY", "secret", "2026-10-07T10:00:00+09:00", "abcdefghijklmnopqrstuvwxyz012345");

        // 기대값: echo -n "2026-10-07T10:00:00+09:00abcdefghijklmnopqrstuvwxyz012345" | openssl dgst -sha256 -hmac secret
        assertThat(header).startsWith("HMAC-SHA256 apiKey=KEY, date=2026-10-07T10:00:00+09:00, "
                + "salt=abcdefghijklmnopqrstuvwxyz012345, signature=");
        assertThat(header).endsWith("signature=4115316093bf3bbe10f5bcd67094f8082adaa8f6985776931d7b43d6bb931c20");
        assertThat(header).as("시크릿 자체는 헤더에 없다").doesNotContain("secret");
    }

    @Test
    @DisplayName("본문: 알림톡(ATA) 한 건, 변수 이름은 #{…} 로 감싸고 문자 대체 발송은 끈다")
    @SuppressWarnings("unchecked")
    void requestBody() {
        AlimtalkMessage m = new AlimtalkMessage("01012345678", AlimtalkTemplate.SHIPPED, "TPL_1",
                Map.of("송장번호", "123456789012"), "본문", "https://shop.test/orders/lookup");

        Map<String, Object> body = SolapiAlimtalkSender.requestBody(m, "KA01PF_TEST", "0212345678");
        Map<String, Object> one = ((List<Map<String, Object>>) body.get("messages")).get(0);
        Map<String, Object> kakao = (Map<String, Object>) one.get("kakaoOptions");

        assertThat(one).containsEntry("to", "01012345678").containsEntry("from", "0212345678")
                .containsEntry("type", "ATA");
        assertThat(kakao).containsEntry("pfId", "KA01PF_TEST").containsEntry("templateId", "TPL_1")
                .containsEntry("disableSms", true);
        assertThat((Map<String, String>) kakao.get("variables")).containsEntry("#{송장번호}", "123456789012");
    }

    @Test
    @DisplayName("응답: 접수 실패 목록이 있으면 실패 — 번호는 예외 메시지에 넣지 않는다")
    void failedMessageList() {
        SolapiAlimtalkSender.checkAccepted("{\"failedMessageList\":[],\"groupInfo\":{}}");

        assertThatThrownBy(() -> SolapiAlimtalkSender.checkAccepted(
                "{\"failedMessageList\":[{\"to\":\"01012345678\",\"statusCode\":\"1062\","
                        + "\"statusMessage\":\"템플릿 불일치\"}]}"))
                .isInstanceOf(AlimtalkSender.AlimtalkException.class)
                .hasMessageContaining("1062")
                .hasMessageNotContaining("01012345678");
    }

    @Test
    @DisplayName("설정이 하나라도 비면 준비 안 됨, 발신번호는 숫자만 남긴다")
    void propertiesReady() {
        assertThat(new SolapiProperties("k", "s", "pf", "02-1234-5678").from()).isEqualTo("0212345678");
        assertThat(new SolapiProperties("k", "s", "pf", "0212345678").ready()).isTrue();
        assertThat(new SolapiProperties("k", "", "pf", "0212345678").ready()).isFalse();
        assertThat(new SolapiProperties("k", "s", "pf", null).ready()).isFalse();
    }
}