package com.rizenfood.api.order;

import java.util.function.Predicate;

/**
 * 주문 한 건의 결제액을 면세·과세로 나눈다 (2026-10-12).
 *
 *   면세 상품 = 면세 상품 금액 − 할인 중 면세 몫
 *   과세 상품 = 과세 상품 금액 − 할인 중 과세 몫
 *   배송비    = 면세 상품만 산 주문이면 면세, 과세 상품이 하나라도 섞이면 과세 (2026-10-13 대표 확인)
 *
 * 할인(할인코드)은 상품 금액 비율로 나눈다.
 * 반올림 차이는 과세 상품 쪽이 받아서 면세 + 과세가 언제나 결제액과 같다.
 *
 * @param taxFreeGoods    면세 상품 금액 (할인 뒤)
 * @param taxableGoods    과세 상품 금액 (할인 뒤)
 * @param shippingFee     배송비
 * @param shippingTaxFree 배송비가 면세인가 (면세 상품만 산 주문)
 */
record TaxSplit(int taxFreeGoods, int taxableGoods, int shippingFee, boolean shippingTaxFree) {

    /** 면세 합계 (면세 상품 + 면세 배송비). */
    int taxFree() {
        return taxFreeGoods + (shippingTaxFree ? shippingFee : 0);
    }

    /** 과세 합계 (과세 상품 + 과세 배송비). */
    int taxable() {
        return taxableGoods + (shippingTaxFree ? 0 : shippingFee);
    }

    static TaxSplit of(Order order, Predicate<OrderItem> isTaxFree) {
        long items = 0;
        long free = 0;
        boolean allFree = !order.getItems().isEmpty();
        for (OrderItem it : order.getItems()) {
            items += it.getLineAmount();
            if (isTaxFree.test(it)) {
                free += it.getLineAmount();
            } else {
                allFree = false;
            }
        }
        int freeDiscount = items == 0 ? 0 : (int) Math.round((double) order.getDiscountAmount() * free / items);
        int taxFreeGoods = (int) free - freeDiscount;
        int shipping = order.getShippingFee();
        return new TaxSplit(taxFreeGoods, order.getTotalAmount() - shipping - taxFreeGoods, shipping, allFree);
    }
}
