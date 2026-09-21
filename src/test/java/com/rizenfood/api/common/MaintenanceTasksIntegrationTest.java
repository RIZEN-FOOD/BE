package com.rizenfood.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 정리 작업이 "도는 척"이 아니라 실제로 지우는지 확인한다.
 *
 * 처음 만들었을 때 스케줄러가 자기 메서드를 직접 불러(프록시 우회) 트랜잭션이 걸리지 않았고,
 * 삭제 쿼리가 매번 실패하는데 로그만 남아 아무도 모르는 상태였다. 그 재발을 막는 테스트다.
 *
 * ★ 트랜잭션 롤백에 기대지 않는다(@Transactional 을 붙이지 않는다).
 *   실제로 커밋돼야 삭제가 됐는지 확인할 수 있다. 넣은 데이터는 테스트가 직접 치운다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long"
})
@Testcontainers(disabledWithoutDocker = true)
class MaintenanceTasksIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // 스케줄러를 통해 부른다 — 작업 본체만 부르면 "스케줄러가 프록시를 우회해 아무것도 안 지우는"
    // 원래 문제를 다시 놓친다.
    @Autowired
    MaintenanceScheduler scheduler;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("오래 방치된 비회원 장바구니는 지워지고, 최근 것은 남는다")
    void deletesOnlyStaleGuestCarts() {
        Instant old = Instant.now().minus(60, ChronoUnit.DAYS);
        Long stale = jdbc.queryForObject(
                "INSERT INTO cart (guest_token, created_at, updated_at) VALUES (?, ?, ?) RETURNING id",
                Long.class, "stale-" + System.nanoTime(), java.sql.Timestamp.from(old),
                java.sql.Timestamp.from(old));
        Long fresh = jdbc.queryForObject(
                "INSERT INTO cart (guest_token) VALUES (?) RETURNING id",
                Long.class, "fresh-" + System.nanoTime());

        scheduler.cleanUp();

        assertThat(exists("cart", stale)).isFalse();
        assertThat(exists("cart", fresh)).isTrue();

        jdbc.update("DELETE FROM cart WHERE id = ?", fresh);
    }

    @Test
    @DisplayName("보존기간이 지난 탈퇴 회원은 파기되고, 남은 회원은 그대로다")
    void purgesWithdrawnMembersPastRetention() {
        Long expired = insertMember("WITHDRAWN", Instant.now().minus(1, ChronoUnit.DAYS));
        Long stillKept = insertMember("WITHDRAWN", Instant.now().plus(10, ChronoUnit.DAYS));
        Long active = insertMember("ACTIVE", null);

        scheduler.cleanUp();

        assertThat(exists("member", expired)).isFalse();
        assertThat(exists("member", stillKept)).isTrue();
        assertThat(exists("member", active)).isTrue();

        jdbc.update("DELETE FROM member WHERE id in (?, ?)", stillKept, active);
    }

    @Test
    @DisplayName("발송한 지 오래된 주문만 배송 완료가 되고, 최근 발송·취소 건은 그대로다")
    void completesOnlyOldShippedDeliveries() {
        Instant old = Instant.now().minus(10, ChronoUnit.DAYS);

        Long oldShipped = insertOrder("SHIPPED");
        insertDelivery(oldShipped, "SHIPPED", old);

        Long justShipped = insertOrder("SHIPPED");
        insertDelivery(justShipped, "SHIPPED", Instant.now());

        // 발송 뒤 손님이 반품해 주문이 배송중이 아니게 된 건 — 건드리면 안 된다
        Long refunded = insertOrder("REFUNDED");
        insertDelivery(refunded, "SHIPPED", old);

        scheduler.cleanUp();

        assertThat(orderStatus(oldShipped)).isEqualTo("DELIVERED");
        assertThat(deliveryStatus(oldShipped)).isEqualTo("DELIVERED");
        assertThat(orderStatus(justShipped)).isEqualTo("SHIPPED");
        assertThat(orderStatus(refunded)).isEqualTo("REFUNDED");
        assertThat(deliveryStatus(refunded)).isEqualTo("SHIPPED");

        jdbc.update("DELETE FROM orders WHERE id IN (?, ?, ?)", oldShipped, justShipped, refunded);
    }

    @Test
    @DisplayName("설정이 0 이면 자동으로 바꾸지 않는다")
    void doesNothingWhenTurnedOff() {
        String before = jdbc.queryForObject(
                "SELECT value FROM site_setting WHERE key = 'shipping.auto_complete_days'", String.class);
        jdbc.update("UPDATE site_setting SET value = '0' WHERE key = 'shipping.auto_complete_days'");

        Long order = insertOrder("SHIPPED");
        insertDelivery(order, "SHIPPED", Instant.now().minus(30, ChronoUnit.DAYS));
        try {
            scheduler.cleanUp();
            assertThat(orderStatus(order)).isEqualTo("SHIPPED");
        } finally {
            jdbc.update("UPDATE site_setting SET value = ? WHERE key = 'shipping.auto_complete_days'", before);
            jdbc.update("DELETE FROM orders WHERE id = ?", order);
        }
    }

    private Long insertOrder(String status) {
        return jdbc.queryForObject(
                "INSERT INTO orders (order_no, status, orderer_name, orderer_phone_encrypted, "
                        + "receiver_name, receiver_phone_encrypted, zipcode, addr1, "
                        + "items_amount, total_amount) "
                        + "VALUES (?, ?, '주문자', 'enc', '받는분', 'enc', '06234', '서울 강남구 1', 1000, 1000) "
                        + "RETURNING id",
                Long.class, "MT" + System.nanoTime(), status);
    }

    private void insertDelivery(Long orderId, String status, Instant shippedAt) {
        jdbc.update("INSERT INTO delivery (order_id, status, shipped_at) VALUES (?, ?, ?)",
                orderId, status, java.sql.Timestamp.from(shippedAt));
    }

    private String orderStatus(Long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String deliveryStatus(Long orderId) {
        return jdbc.queryForObject("SELECT status FROM delivery WHERE order_id = ?", String.class, orderId);
    }

    private Long insertMember(String status, Instant purgeAt) {
        return jdbc.queryForObject(
                "INSERT INTO member (email, name, status, purge_at) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                "maintenance+" + System.nanoTime() + "@rizen.invalid", "테스트", status,
                purgeAt == null ? null : java.sql.Timestamp.from(purgeAt));
    }

    private boolean exists(String table, Long id) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
}
