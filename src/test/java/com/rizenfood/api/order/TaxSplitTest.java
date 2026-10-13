package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 결제액을 면세·과세로 나누는 계산 (2026-10-12, 배송비 규칙 2026-10-13). */
class TaxSplitTest {

    private static Order order(int shipping, int discount, OrderItem... items) {
        Order o = new Order();
        int sum = 0;
        for (OrderItem it : items) {
            o.addItem(it);
            sum += it.getLineAmount();
        }
        o.setItemsAmount(sum);
        o.setShippingFee(shipping);
        o.setDiscountAmount(discount);
        o.setTotalAmount(sum + shipping - discount);
        return o;
    }

    private static OrderItem plain(int price) {
        return new OrderItem(1L, null, "플레인", null, null, price, 1, true);
    }

    private static OrderItem choco(int price) {
        return new OrderItem(2L, null, "초코", null, null, price, 1, false);
    }

    private static TaxSplit split(Order o) {
        return TaxSplit.of(o, it -> Boolean.TRUE.equals(it.getTaxFree()));
    }

    @Test
    @DisplayName("면세·과세가 섞이면 할인은 상품 금액 비율로 나누고, 배송비는 과세")
    void mixedOrderShippingIsTaxable() {
        TaxSplit s = split(order(3_500, 3_000, plain(10_000), choco(5_000)));

        assertThat(s.taxFreeGoods()).isEqualTo(10_000 - 2_000);   // 할인 3,000 의 2/3
        assertThat(s.taxableGoods()).isEqualTo(5_000 - 1_000);    // 나머지 1/3
        assertThat(s.shippingTaxFree()).isFalse();
        assertThat(s.taxFree()).isEqualTo(8_000);
        assertThat(s.taxable()).isEqualTo(4_000 + 3_500);
        assertThat(s.taxFree() + s.taxable()).isEqualTo(15_500);
    }

    @Test
    @DisplayName("면세 상품만 산 주문은 배송비도 면세")
    void taxFreeOnlyOrderShippingIsTaxFree() {
        TaxSplit s = split(order(3_500, 0, plain(12_900)));

        assertThat(s.shippingTaxFree()).isTrue();
        assertThat(s.taxFree()).isEqualTo(12_900 + 3_500);
        assertThat(s.taxable()).isZero();
    }

    @Test
    @DisplayName("과세 상품만 산 주문은 전부 과세")
    void taxableOnlyOrder() {
        TaxSplit s = split(order(3_500, 0, choco(19_900)));

        assertThat(s.shippingTaxFree()).isFalse();
        assertThat(s.taxFree()).isZero();
        assertThat(s.taxable()).isEqualTo(19_900 + 3_500);
    }
}
