package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 결제액을 면세·과세로 나누는 계산 (2026-10-12). */
class TaxSplitTest {

    /** 면세 10,000 + 과세 5,000, 배송비 3,500, 할인 3,000. */
    private static Order order() {
        Order o = new Order();
        o.addItem(new OrderItem(1L, null, "플레인", null, null, 10_000, 1, true));
        o.addItem(new OrderItem(2L, null, "초코", null, null, 5_000, 1, false));
        o.setItemsAmount(15_000);
        o.setShippingFee(3_500);
        o.setDiscountAmount(3_000);
        o.setTotalAmount(15_000 + 3_500 - 3_000);
        return o;
    }

    @Test
    @DisplayName("할인은 상품 금액 비율로 나누고, 배송비는 과세 — 면세 + 과세 = 결제액")
    void splitsDiscountByRatio() {
        TaxSplit s = TaxSplit.of(order(), it -> Boolean.TRUE.equals(it.getTaxFree()));

        assertThat(s.taxFree()).isEqualTo(10_000 - 2_000);      // 할인 3,000 의 2/3
        assertThat(s.taxableGoods()).isEqualTo(5_000 - 1_000);  // 나머지 1/3
        assertThat(s.shippingFee()).isEqualTo(3_500);
        assertThat(s.taxFree() + s.taxable()).isEqualTo(15_500);
    }

    @Test
    @DisplayName("전액 환불은 결제 때 나눈 그대로, 일부 환불은 같은 비율로")
    void splitsRefund() {
        TaxSplit s = TaxSplit.of(order(), it -> Boolean.TRUE.equals(it.getTaxFree()));

        TaxSplit.Refund full = s.refund(15_500, 15_500);
        assertThat(full.taxFree()).isEqualTo(8_000);
        assertThat(full.taxable()).isEqualTo(7_500);

        TaxSplit.Refund part = s.refund(15_500, 3_100);
        assertThat(part.taxFree() + part.taxable()).isEqualTo(3_100);
        assertThat(part.taxFree()).isEqualTo(Math.round(3_100 * 8_000 / 15_500.0));
    }
}
