package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 나이스 위변조 서명.
 *
 * 나이스는 결제 결과를 <b>브라우저를 통해</b> 우리 서버로 POST 한다. 다른 사이트에서 오는
 * 이동이라 우리 쿠키가 실리지 않아, 누가 보냈는지 알 방법이 이 서명뿐이다.
 * 서명 검증이 뚫리면 "결제했다"는 가짜 요청으로 주문을 확정시킬 수 있다.
 */
class NicePaySignatureTest {

    private static final String CLIENT_ID = "R2_test_client_id";
    private static final String SECRET = "test-secret-key";

    private NicePayGateway gateway() {
        // 키가 있는 상태로 만든다(HTTP 는 이 테스트에서 쓰지 않는다).
        return new NicePayGateway(null, CLIENT_ID, SECRET, "", "https://api.nicepay.co.kr");
    }

    @Test
    @DisplayName("문서에 적힌 규칙 그대로 계산한다 — sha256(authToken + clientId + amount + secretKey)")
    void verifiesGenuineSignature() {
        String authToken = "authToken-abc123";
        String amount = "15900";
        String signature = NicePayGateway.sha256Hex(authToken + CLIENT_ID + amount + SECRET);

        assertThat(gateway().verifyAuthSignature(authToken, amount, signature)).isTrue();
        // 대소문자가 달라도 같은 값으로 본다 (PG 마다 표기가 다르다)
        assertThat(gateway().verifyAuthSignature(authToken, amount, signature.toUpperCase())).isTrue();
    }

    @Test
    @DisplayName("금액을 올려 보내면 서명이 깨져 거부된다")
    void rejectsTamperedAmount() {
        String authToken = "authToken-abc123";
        String signature = NicePayGateway.sha256Hex(authToken + CLIENT_ID + "15900" + SECRET);

        // 공격자가 금액만 1원으로 바꿔 보낸 경우
        assertThat(gateway().verifyAuthSignature(authToken, "1", signature)).isFalse();
    }

    @Test
    @DisplayName("시크릿을 모르면 서명을 만들 수 없다")
    void rejectsSignatureMadeWithWrongSecret() {
        String authToken = "authToken-abc123";
        String amount = "15900";
        String forged = NicePayGateway.sha256Hex(authToken + CLIENT_ID + amount + "guessed-secret");

        assertThat(gateway().verifyAuthSignature(authToken, amount, forged)).isFalse();
    }

    @Test
    @DisplayName("서명이 비어 있으면 거부한다 — 빈 값이 통과하면 검증이 없는 것과 같다")
    void rejectsMissingSignature() {
        assertThat(gateway().verifyAuthSignature("t", "1000", null)).isFalse();
        assertThat(gateway().verifyAuthSignature("t", "1000", "")).isFalse();
        assertThat(gateway().verifyAuthSignature("t", "1000", "   ")).isFalse();
    }

    @Test
    @DisplayName("해시는 소문자 16진수 64자리")
    void producesLowercaseHex() {
        String hex = NicePayGateway.sha256Hex("hello");
        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
        // 알려진 값과 대조 — 구현이 바뀌어도 규칙이 유지되는지 본다
        assertThat(hex).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
