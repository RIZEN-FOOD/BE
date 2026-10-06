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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.order.OrderService;
import com.rizenfood.api.order.dto.OrderDtos;

/**
 * 주문 알림톡 (2026-10-06).
 *
 * 실제 업체로 나가지 않게 가짜 발송기를 끼우고, 언제 무엇이 나가는지 본다.
 * ★ 결제가 확정되면 «결제 완료», 송장이 들어가면 «출고 안내» — 주문자 번호로.
 * ★ 커밋된 뒤에만 보낸다. 롤백된 일에는 알림이 가지 않는다.
 * ★ 같은 송장을 다시 저장하면 출고 알림을 또 보내지 않는다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false",
        "app.site-url=https://shop.test",
        "app.alimtalk.enabled=true",
        "app.alimtalk.provider=test",
        "app.alimtalk.template-paid=TPL_PAID",
        "app.alimtalk.template-shipped=TPL_SHIPPED"
})
@Testcontainers(disabledWithoutDocker = true)
class AlimtalkIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 업체 대신. 받은 알림을 모아 둔다. */
    static class FakeSender implements AlimtalkSender {
        final List<AlimtalkMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public void send(AlimtalkMessage message) {
            sent.add(message);
        }
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        FakeSender fakeSender() {
            return new FakeSender();
        }
    }

    @Autowired
    FakeSender fake;

    @Autowired
    OrderService orderService;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        fake.sent.clear();
    }

    private String newOrder(String ordererPhone) {
        Long productId = jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible) VALUES (?, ?, ?, ?, true) RETURNING id",
                Long.class, "alim-" + System.nanoTime(), "크림오브라이스", 12_900, 10);
        return orderService.createDirect(null, new OrderDtos.CreateRequest(
                "김라이", ordererPhone, null, "이즌", "010-9999-8888",
                "06236", "서울 강남구", null, null, null,
                List.of(new OrderDtos.DirectItem(productId, null, 2)))).orderNo();
    }

    /** 다른 스레드에서 보내므로 잠깐 기다린다. */
    private void awaitSent(int count) throws InterruptedException {
        for (int i = 0; i < 50 && fake.sent.size() < count; i++) {
            Thread.sleep(100);
        }
    }

    @Test
    @DisplayName("결제가 확정되면 주문자 번호로 «결제 완료» — 주문번호·상품명·금액이 채워진다")
    void paid() throws Exception {
        String orderNo = newOrder("010-1234-5678");

        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        awaitSent(1);

        assertThat(fake.sent).hasSize(1);
        AlimtalkMessage m = fake.sent.get(0);
        assertThat(m.template()).isEqualTo(AlimtalkTemplate.PAID);
        assertThat(m.templateCode()).isEqualTo("TPL_PAID");
        assertThat(m.to()).as("받는 분이 아니라 주문자 번호, 숫자만").isEqualTo("01012345678");
        assertThat(m.variables()).containsEntry("고객명", "김라이")
                .containsEntry("주문번호", orderNo)
                .containsEntry("상품명", "크림오브라이스")
                .containsEntry("결제금액", "29,300");
        assertThat(m.text()).contains(orderNo).doesNotContain("#{");
        assertThat(m.buttonUrl()).isEqualTo("https://shop.test/orders/lookup");
        assertThat(m.toString()).as("로그에 번호가 그대로 찍히지 않는다").doesNotContain("12345678");
    }

    @Test
    @DisplayName("송장이 들어가면 «출고 안내» — 같은 송장을 다시 저장하면 또 보내지 않고, 송장이 바뀌면 다시 보낸다")
    void shipped() throws Exception {
        String orderNo = newOrder("010-2222-3333");
        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        awaitSent(1);
        fake.sent.clear();

        orderService.adminShip(orderNo, "롯데택배", "123456789012");
        awaitSent(1);
        assertThat(fake.sent).hasSize(1);
        AlimtalkMessage m = fake.sent.get(0);
        assertThat(m.template()).isEqualTo(AlimtalkTemplate.SHIPPED);
        assertThat(m.variables()).containsEntry("택배사", "롯데택배").containsEntry("송장번호", "123456789012");

        orderService.adminShip(orderNo, "롯데택배", "123456789012");
        Thread.sleep(500);
        assertThat(fake.sent).as("두 번 누름").hasSize(1);

        orderService.adminShip(orderNo, "롯데택배", "999988887777");
        awaitSent(2);
        assertThat(fake.sent).hasSize(2);
        assertThat(fake.sent.get(1).variables()).containsEntry("송장번호", "999988887777");
    }

    @Test
    @DisplayName("롤백된 트랜잭션의 사건은 보내지 않는다")
    void rolledBackIsNotSent() throws Exception {
        String orderNo = newOrder("010-4444-5555");
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);

        tx.executeWithoutResult(status -> {
            events.publishEvent(new OrderNotificationEvent(orderId, OrderNotificationEvent.Type.PAID));
            status.setRollbackOnly();
        });
        Thread.sleep(500);

        assertThat(fake.sent).isEmpty();
    }

    @Test
    @DisplayName("템플릿 문구: 빈 변수가 있으면 보내지 않는다(빈칸 알림 금지)")
    void renderRequiresAllVariables() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AlimtalkTemplate.SHIPPED.render(
                java.util.Map.of("고객명", "김라이", "주문번호", "R1", "상품명", "쌀", "택배사", "롯데택배")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("송장번호");
    }
}
