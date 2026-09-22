package com.rizenfood.api.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 배송비 계산 규칙 (CLAUDE.md 규칙 5).
 *
 * 임계액을 코드에 박지 않고 정책에서 읽는다. 여기서는 계산 로직만 검증한다.
 * 기본 배송비 3,000원 / 50,000원 이상 무료 정책을 가정한다.
 */
class ShippingPolicyFeeTest {

    private ShippingPolicy policy(int baseFee, Integer freeThreshold) {
        ShippingPolicy p = new ShippingPolicy();
        ReflectionTestUtils.setField(p, "baseFee", baseFee);
        ReflectionTestUtils.setField(p, "freeThreshold", freeThreshold);
        return p;
    }

    @Test
    @DisplayName("빈 장바구니(0원)는 배송비 0")
    void zeroWhenEmpty() {
        assertThat(policy(3000, 50000).feeFor(0)).isZero();
    }

    @Test
    @DisplayName("임계액 미만이면 기본 배송비")
    void baseFeeBelowThreshold() {
        assertThat(policy(3000, 50000).feeFor(49_999)).isEqualTo(3000);
    }

    @Test
    @DisplayName("임계액 이상이면 무료 (경계 포함)")
    void freeAtOrAboveThreshold() {
        assertThat(policy(3000, 50000).feeFor(50_000)).isZero();
        assertThat(policy(3000, 50000).feeFor(80_000)).isZero();
    }

    @Test
    @DisplayName("무료 임계액이 없으면 항상 기본 배송비")
    void alwaysBaseFeeWhenNoThreshold() {
        assertThat(policy(3000, null).feeFor(1_000_000)).isEqualTo(3000);
    }

    private ShippingPolicy islandPolicy() {
        ShippingPolicy p = policy(3500, 50000);
        ReflectionTestUtils.setField(p, "islandExtraFee", 3000);
        return p;
    }

    @Test
    @DisplayName("도서산간이면 기본 배송비에 추가분을 더한다")
    void islandAddsExtra() {
        assertThat(islandPolicy().feeFor(30_000, true)).isEqualTo(6500);
        assertThat(islandPolicy().feeFor(30_000, false)).isEqualTo(3500);
    }

    @Test
    @DisplayName("무료배송 금액을 넘어도 도서산간 추가분은 받는다")
    void islandExtraEvenWhenFree() {
        assertThat(islandPolicy().feeFor(50_000, true)).isEqualTo(3000);
        assertThat(islandPolicy().feeFor(50_000, false)).isZero();
    }

    @Test
    @DisplayName("빈 장바구니는 도서산간이어도 0")
    void islandZeroWhenEmpty() {
        assertThat(islandPolicy().feeFor(0, true)).isZero();
    }

    // ── 할인코드를 뺀 뒤의 배송비 (2026-09-22) ───────────────────

    @Test
    @DisplayName("할인 뒤 금액이 무료배송 금액 아래로 내려가면 배송비를 받는다")
    void feeReturnsWhenDiscountDropsBelowThreshold() {
        // 51,600원어치를 담아 무료였지만, 5,000원을 깎으면 46,600원이라 다시 배송비가 붙는다
        assertThat(islandPolicy().feeFor(51_600, 46_600, false)).isEqualTo(3500);
    }

    @Test
    @DisplayName("할인을 빼도 무료배송 금액 이상이면 그대로 무료")
    void stillFreeWhenDiscountedAmountReachesThreshold() {
        assertThat(islandPolicy().feeFor(60_000, 55_000, false)).isZero();
    }

    @Test
    @DisplayName("코드로 전액이 깎여도 물건은 나가므로 배송비는 받는다")
    void feeChargedEvenWhenFullyDiscounted() {
        assertThat(islandPolicy().feeFor(30_000, 0, false)).isEqualTo(3500);
    }

    @Test
    @DisplayName("빈 장바구니는 할인 기준으로 봐도 0")
    void zeroWhenEmptyEvenWithDiscount() {
        assertThat(islandPolicy().feeFor(0, 0, true)).isZero();
    }

    @Test
    @DisplayName("도서산간 추가분은 할인과 무관하게 붙는다")
    void islandExtraIndependentOfDiscount() {
        assertThat(islandPolicy().feeFor(60_000, 55_000, true)).isEqualTo(3000);
        assertThat(islandPolicy().feeFor(51_600, 46_600, true)).isEqualTo(6500);
    }
}
