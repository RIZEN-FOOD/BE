package com.rizenfood.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * 결제 결과 주소와 CORS (2026-10-07).
 *
 * 나이스페이 결제창이 결과를 form POST 로 보낼 때 브라우저가 Origin(나이스페이 도메인)을 붙인다.
 * 이 주소가 CORS 에 막히면(403 Invalid CORS request) 운영 결제가 승인 단계로 넘어가지 못한다.
 * ★ 다른 API 는 지금처럼 다른 사이트 Origin 을 막아야 한다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentReturnCorsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("나이스페이 도메인에서 온 결제 결과 POST 는 CORS 로 막지 않는다")
    void paymentReturnNotBlockedByCors() throws Exception {
        int status = mvc.perform(post("/api/payment/nicepay/return")
                        .header("Origin", "https://pay.nicepay.co.kr")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("authResultCode=9999&orderId=TEST"))
                .andReturn().getResponse().getStatus();

        // 테스트 환경은 모의 결제라 이 주소가 없어 404 가 날 수 있다. 막히지만 않으면 된다(403 이 아님).
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    @DisplayName("다른 API 는 다른 사이트 Origin 을 지금처럼 막는다")
    void otherApisStillBlockForeignOrigin() throws Exception {
        int status = mvc.perform(post("/api/inquiries")
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
