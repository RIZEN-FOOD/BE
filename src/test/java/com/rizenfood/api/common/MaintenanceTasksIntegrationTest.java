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
