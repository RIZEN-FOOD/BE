package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영(prod) 설정으로 실제로 띄워 본다 — 배포 당일 서버가 안 뜨는 일을 미리 막는다.
 *
 *  - PG 키가 아직 없어도 서버가 뜬다
 *  - 결제 게이트웨이는 포트원(결제 받지 않는 상태)이고, 모의 결제는 아예 로드되지 않는다
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.payment.portone.api-secret=",
        // 운영 프로필에서 환경변수로 모의 결제를 켜려 해도 무시돼야 한다
        "PAYMENT_PROVIDER=mock",
        "app.storage.local.path=build/tmp/prod-startup-uploads"
})
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class ProdProfileStartupTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ApplicationContext context;

    @Autowired
    PaymentGateway gateway;

    @Test
    @DisplayName("운영 설정 + PG 키 없음 → 서버는 뜨고, 결제는 받지 않는 포트원 상태")
    void prodStartsWithoutPgKeys() {
        assertThat(gateway).isInstanceOf(PortOnePaymentGateway.class);
        assertThat(((PortOnePaymentGateway) gateway).configured()).isFalse();
        assertThat(context.getBeanNamesForType(MockPaymentGateway.class)).isEmpty();
    }
}
