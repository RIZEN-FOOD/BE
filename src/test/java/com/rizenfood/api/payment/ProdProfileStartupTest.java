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
 *  - PG 키가 아직 없어도 서버가 뜬다 (키를 넣기 전에도 사이트는 열려 있어야 한다)
 *  - 결제 게이트웨이는 계약한 PG(나이스페이)이고, <b>모의 결제는 아예 로드되지 않는다</b>
 *
 * 2026-09-21 바뀐 점 — 예전에는 운영 설정에 PG 이름을 못 박아 PAYMENT_PROVIDER 를 무시했다.
 * 그러면 PG 를 바꿀 때마다 코드를 고쳐야 해서, 지금은 환경변수로 고르되
 * {@link PaymentProviderGuard} 가 허용 목록(nicepay·portone)을 벗어나면 서버를 띄우지 않는다.
 * 모의 결제·오타·빈 값이 조용히 통과하는 일은 그대로 막힌다 (PaymentProviderGuardTest).
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        // PG 키를 아직 못 받은 상태를 흉내 낸다
        "app.payment.nicepay.client-id=",
        "app.payment.nicepay.secret-key=",
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
    @DisplayName("운영 설정 + PG 키 없음 → 서버는 뜨고, 결제는 받지 않는 나이스페이 상태")
    void prodStartsWithoutPgKeys() {
        assertThat(gateway).isInstanceOf(NicePayGateway.class);
        // 키가 없으니 결제를 받지 않는다. 결제 화면도 결제수단을 띄우지 않아 버튼이 막힌다.
        assertThat(((NicePayGateway) gateway).configured()).isFalse();
        // ★ 모의 결제가 운영에 끼어들면 돈을 받지 않고 주문이 확정된다. 아예 없어야 한다.
        assertThat(context.getBeanNamesForType(MockPaymentGateway.class)).isEmpty();
    }
}
