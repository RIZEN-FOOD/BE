package com.rizenfood.api.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 할인코드 규칙.
 *
 * 금액을 깎는 기능이라 잘못 만들면 곧장 손해로 이어진다. 특히
 * "수량 한도를 넘겨 쓸 수 있는가"는 동시에 들어온 주문에서 터지므로 테스트로 못 박는다.
 *
 * ★ take()/giveBack() 은 @Modifying 이라 트랜잭션 안에서만 부를 수 있다.
 *   그 둘을 직접 쓰는 테스트에만 @Transactional 을 단다 (끝나면 롤백된다).
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class CouponServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CouponService couponService;

    @Autowired
    CouponRepository couponRepository;

    @Autowired
    JdbcTemplate jdbc;

    private Long made;

    @AfterEach
    void cleanUp() {
        if (made != null) {
            jdbc.update("DELETE FROM coupon WHERE id = ?", made);
            made = null;
        }
    }

    // ── 할인액 계산 ───────────────────────────────────────────

    @Test
    @DisplayName("정률 할인에 상한이 있으면 상한에서 자른다")
    void capsPercentDiscount() {
        Coupon c = coupon(b -> {
            b.setDiscountType(Coupon.PERCENT);
            b.setDiscountValue(10);
            b.setMaxDiscount(5000);
        });

        assertThat(c.discountFor(30_000)).isEqualTo(3_000);  // 상한 아래
        assertThat(c.discountFor(100_000)).isEqualTo(5_000); // 상한에서 잘림
    }

    @Test
    @DisplayName("할인액은 상품 금액을 넘지 않는다 — 총액이 음수가 될 수 없다")
    void neverExceedsItemsAmount() {
        Coupon c = coupon(b -> {
            b.setDiscountType(Coupon.AMOUNT);
            b.setDiscountValue(50_000);
        });

        assertThat(c.discountFor(12_900)).isEqualTo(12_900);
    }

    // ── 사용 조건 ─────────────────────────────────────────────

    @Test
    @DisplayName("꺼둔 코드는 기간 안이라도 쓸 수 없다")
    void refusesHiddenCoupon() {
        made = save(coupon(b -> b.setVisible(false)));

        assertThatThrownBy(() -> couponService.check("RIZENTEST", 30_000, null, null))
                .isInstanceOf(CouponService.RejectedException.class)
                .hasMessageContaining("쓸 수 없는");
    }

    @Test
    @DisplayName("기간이 끝나면 쓸 수 없다")
    void refusesExpired() {
        made = save(coupon(b -> {
            b.setStartAt(Instant.now().minus(Duration.ofDays(10)));
            b.setEndAt(Instant.now().minus(Duration.ofDays(1)));
        }));

        assertThatThrownBy(() -> couponService.check("RIZENTEST", 30_000, null, null))
                .isInstanceOf(CouponService.RejectedException.class)
                .hasMessageContaining("기간이 끝난");
    }

    @Test
    @DisplayName("최소 주문금액에 못 미치면 얼마부터 되는지 알려준다")
    void refusesBelowMinimum() {
        made = save(coupon(b -> b.setMinOrderAmount(30_000)));

        assertThatThrownBy(() -> couponService.check("RIZENTEST", 12_900, null, null))
                .isInstanceOf(CouponService.RejectedException.class)
                .hasMessageContaining("30,000원 이상");
    }

    @Test
    @DisplayName("없는 코드는 존재 여부를 흘리지 않는 문구로 막는다")
    void refusesUnknownCode() {
        assertThatThrownBy(() -> couponService.check("NOSUCHCODE", 30_000, null, null))
                .isInstanceOf(CouponService.RejectedException.class)
                .hasMessageContaining("없는 할인코드");
    }

    @Test
    @DisplayName("소문자로 입력해도 같은 코드로 본다")
    void codeIsCaseInsensitive() {
        made = save(coupon(b -> { }));

        assertThat(couponService.check("rizentest", 30_000, null, null).discount())
                .isEqualTo(3_000);
    }

    // ── 수량 한도 ─────────────────────────────────────────────

    @Test
    @DisplayName("총 수량을 넘겨 쓸 수 없다 — 한도만큼만 확보된다")
    @Transactional
    void takeStopsAtTotalQuantity() {
        made = save(coupon(b -> b.setTotalQuantity(2)));

        assertThat(couponRepository.take(made)).isEqualTo(1);
        assertThat(couponRepository.take(made)).isEqualTo(1);
        assertThat(couponRepository.take(made))
                .as("한도를 넘는 세 번째는 0 이어야 한다")
                .isEqualTo(0);

        assertThatThrownBy(() -> couponService.check("RIZENTEST", 30_000, null, null))
                .isInstanceOf(CouponService.RejectedException.class)
                .hasMessageContaining("모두 사용");
    }

    @Test
    @DisplayName("취소하면 한 장이 되돌아온다. 0 아래로는 안 내려간다")
    @Transactional
    void giveBackNeverGoesNegative() {
        made = save(coupon(b -> b.setTotalQuantity(1)));

        couponRepository.take(made);
        couponService.release(made);
        assertThat(couponRepository.findById(made).orElseThrow().getUsedCount()).isZero();

        couponService.release(made);
        assertThat(couponRepository.findById(made).orElseThrow().getUsedCount())
                .as("이미 0 이면 그대로 0")
                .isZero();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private interface Tweak {
        void apply(Coupon c);
    }

    /** 10% · 기간 안 · 켜짐 을 기본으로 둔 코드 한 장. */
    private Coupon coupon(Tweak tweak) {
        Coupon c = new Coupon();
        c.setName("테스트 코드");
        c.setCode("RIZENTEST");
        c.setDiscountType(Coupon.PERCENT);
        c.setDiscountValue(10);
        c.setMinOrderAmount(0);
        c.setPerMemberLimit(1);
        c.setStartAt(Instant.now().minus(Duration.ofDays(1)));
        c.setEndAt(Instant.now().plus(Duration.ofDays(1)));
        c.setVisible(true);
        tweak.apply(c);
        return c;
    }

    private Long save(Coupon c) {
        return couponRepository.save(c).getId();
    }
}
