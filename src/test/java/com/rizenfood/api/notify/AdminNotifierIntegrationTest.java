package com.rizenfood.api.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.order.ClaimService;
import com.rizenfood.api.order.OrderService;
import com.rizenfood.api.order.dto.ClaimDtos;
import com.rizenfood.api.order.dto.OrderDtos;

/**
 * 운영자 알림 문자 (2026-10-16).
 *
 * ★ 결제가 확정되면 «새 주문», 손님이 취소를 요청하면 «취소 요청» — 운영자 번호마다 한 통씩.
 * ★ 손님 이름·연락처는 문자에 넣지 않는다. 결제 전 주문은 알리지 않는다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false",
        "app.admin-notify.enabled=true",
        "app.admin-notify.phones=010-1111-0000, 01022220000"
})
@Testcontainers(disabledWithoutDocker = true)
class AdminNotifierIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 실제 업체 대신 — 받은 문자를 모아 둔다. */
    static class FakeSms implements SmsSender {
        record Sent(String to, String text) {
        }

        final List<Sent> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String text) {
            sent.add(new Sent(to, text));
        }
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        @Primary
        FakeSms fakeSms() {
            return new FakeSms();
        }
    }

    @Autowired
    FakeSms fake;

    @Autowired
    OrderService orderService;

    @Autowired
    ClaimService claimService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        fake.sent.clear();
    }

    private String newOrder() {
        Long productId = jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible) VALUES (?, ?, ?, ?, true) RETURNING id",
                Long.class, "adm-" + System.nanoTime(), "크림오브라이스", 12_900, 10);
        return orderService.createDirect(null, new OrderDtos.CreateRequest(
                "김라이", "010-5555-6666", null, "이즌", "010-7777-8888",
                "06236", "서울 강남구", null, null, null,
                List.of(new OrderDtos.DirectItem(productId, null, 1)))).orderNo();
    }

    private void awaitSent(int count) throws InterruptedException {
        for (int i = 0; i < 50 && fake.sent.size() < count; i++) {
            Thread.sleep(100);
        }
    }

    @Test
    @DisplayName("결제가 확정되면 운영자 번호마다 «새 주문» 문자 — 손님 이름·연락처는 없다")
    void newOrderTexts() throws Exception {
        String orderNo = newOrder();
        Thread.sleep(300);
        assertThat(fake.sent).as("결제 전에는 보내지 않는다").isEmpty();

        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        awaitSent(2);

        assertThat(fake.sent).extracting(FakeSms.Sent::to).containsExactly("01011110000", "01022220000");
        String text = fake.sent.get(0).text();
        assertThat(text).contains("새 주문", orderNo, "크림오브라이스", "원")
                .doesNotContain("김라이", "55556666", "강남");
    }

    @Test
    @DisplayName("손님이 취소를 요청하면 «취소 요청» 문자")
    void claimTexts() throws Exception {
        String orderNo = newOrder();
        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        awaitSent(2);
        fake.sent.clear();

        claimService.create(orderNo, null, new ClaimDtos.CreateRequest("CANCEL", "CHANGE_MIND", null));
        awaitSent(2);

        assertThat(fake.sent).hasSize(2);
        assertThat(fake.sent.get(0).text()).contains("취소 요청", orderNo);
    }
}
