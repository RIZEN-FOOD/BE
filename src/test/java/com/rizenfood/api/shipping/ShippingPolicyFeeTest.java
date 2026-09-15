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
}
