package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 취소·반품·교환 요청 시각 (2026-10-01).
 *
 * 전자상거래법상 요청 시각을 반드시 남긴다(CLAUDE.md 규칙 7). DB 에는 기본값으로 남고 있었지만,
 * 저장 직후 응답에는 값이 비어 손님 화면에 "- 접수"로 찍혔다. 만드는 순간 시각이 있어야 한다.
 */
class OrderClaimTimestampTest {

    @Test
    @DisplayName("요청을 만드는 순간 요청 시각이 채워진다 — 저장 직후 응답에도 시각이 보인다")
    void requestedAtIsSetOnCreation() {
        Instant before = Instant.now();

        OrderClaim claim = new OrderClaim(1L, OrderClaim.Type.CANCEL, "CHANGE_OF_MIND", null);

        assertThat(claim.getRequestedAt()).isNotNull();
        assertThat(Duration.between(before, claim.getRequestedAt()).abs()).isLessThan(Duration.ofSeconds(5));
        assertThat(claim.getProcessedAt()).as("처리 시각은 처리할 때 채운다").isNull();
    }
}
