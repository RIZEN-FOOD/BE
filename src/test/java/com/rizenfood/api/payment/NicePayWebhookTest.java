package com.rizenfood.api.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 나이스 결과 통보(웹훅) 받기.
 *
 * ★ 나이스는 응답 본문에 "OK" 가 없으면 실패로 보고 계속 재전송한다.
 *   형식이 틀리면 결제는 되는데 통보가 몇 시간씩 반복해서 들어온다. 그래서 형식을 테스트로 고정한다.
 * ★ 로그인 없이 열린 주소이므로, 서명이 틀린 요청으로 주문이 확정되지 않아야 한다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false",
        "app.payment.provider=nicepay",
        "app.payment.nicepay.client-id=R2_test_client",
        "app.payment.nicepay.secret-key=test-secret"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NicePayWebhookTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("나이스가 요구하는 대로 text/html 로 OK 를 돌려준다")
    void answersOkAsHtml() throws Exception {
        String tid = "nicuntct1m01012107272007";
        String amount = "15900";
        String ediDate = "2026-09-21T12:00:00.000+09:00";
        String signature = NicePayGateway.sha256Hex(tid + amount + ediDate + "test-secret");

        String body = """
                {"resultCode":"0000","tid":"%s","orderId":"R20260921-NOSUCHORDER",
                 "amount":%s,"ediDate":"%s","signature":"%s","status":"paid"}
                """.formatted(tid, amount, ediDate, signature);

        mvc.perform(post("/api/payment/webhook/nicepay")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("서명이 틀려도 OK 로 받는다 — 처리하지 않을 뿐이다(재전송을 부르지 않는다)")
    void acceptsButIgnoresBadSignature() throws Exception {
        String body = """
                {"resultCode":"0000","tid":"tid-forged","orderId":"R20260921-FORGED",
                 "amount":1,"ediDate":"2026-09-21T12:00:00.000+09:00",
                 "signature":"deadbeef","status":"paid"}
                """;

        mvc.perform(post("/api/payment/webhook/nicepay")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("콘솔의 빈 테스트 호출도 OK 로 받는다")
    void acceptsEmptyTestCall() throws Exception {
        mvc.perform(post("/api/payment/webhook/nicepay")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("로그인 없이 부를 수 있다 — 서버끼리 오는 요청이라 쿠키가 없다")
    void isOpenWithoutLogin() throws Exception {
        mvc.perform(post("/api/payment/webhook/nicepay")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
}
