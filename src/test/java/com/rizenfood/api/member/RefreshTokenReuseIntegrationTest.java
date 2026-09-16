package com.rizenfood.api.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 리프레시 토큰 재사용(탈취 의심) 대응이 실제로 먹히는지 확인한다.
 *
 * 이미 무효화된 토큰이 다시 들어오면 그 회원의 토큰을 전부 끊어야 한다.
 * 처음 구현에서는 끊자마자 던진 예외가 그 무효화까지 되돌려, 막은 것처럼 보이지만
 * 공격자의 토큰이 그대로 살아 있었다. 그 재발을 막는 테스트다.
 *
 * ★ @Transactional 을 붙이지 않는다. 실제로 커밋돼야 "정말 끊겼는지" 확인할 수 있다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long"
})
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenReuseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MemberAuthService authService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("무효화된 토큰이 다시 들어오면 그 회원의 세션을 전부 끊는다")
    void reuseRevokesWholeFamily() {
        Long memberId = jdbc.queryForObject(
                "INSERT INTO member (email, name) VALUES (?, ?) RETURNING id",
                Long.class, "reuse+" + System.nanoTime() + "@rizen.invalid", "테스트");

        // 손님이 로그인해 토큰을 받았고, 그 토큰이 새어 나갔다고 본다.
        String stolen = authService.issueRefreshToken(memberId, "ua", "127.0.0.1");

        // 공격자가 먼저 갱신한다 → 공격자만 쓸 수 있는 새 토큰이 생긴다.
        MemberAuthService.RefreshResult attacker = authService.rotate(stolen, "ua", "10.0.0.9");
        assertThat(livingTokens(memberId)).isEqualTo(1);

        // 나중에 진짜 손님이 예전 토큰으로 갱신을 시도한다 → 재사용 감지.
        assertThatThrownBy(() -> authService.rotate(stolen, "ua", "127.0.0.1"))
                .isInstanceOf(MemberAuthException.class);

        // 공격자가 앞서 받아 둔 토큰까지 죽어야 한다.
        assertThat(livingTokens(memberId)).isZero();
        assertThatThrownBy(() -> authService.rotate(attacker.newRefreshRaw(), "ua", "10.0.0.9"))
                .isInstanceOf(MemberAuthException.class);

        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
    }

    /** 아직 쓸 수 있는(무효화되지 않은) 토큰 수 */
    private int livingTokens(Long memberId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE member_id = ? AND revoked_at IS NULL",
                Integer.class, memberId);
        return count == null ? 0 : count;
    }
}
