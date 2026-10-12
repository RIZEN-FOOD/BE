package com.rizenfood.api.order;

import java.util.function.Predicate;

/**
 * 주문 한 건의 결제액을 면세·과세로 나눈다 (2026-10-12).
 *
 *   면세 = 면세 상품 금액 − 할인 중 면세 몫
 *   과세 = 과세 상품 금액 − 할인 중 과세 몫 + 배송비
 *
 * 할인(할인코드)은 상품 금액 비율로 나눈다. 배송비는 과세다.
 * 반올림 차이는 과세 쪽이 받아서 면세 + 과세가 언제나 결제액과 같다.
 *
 * @param taxFree      면세 금액
 * @param taxableGoods 과세 상품 금액 (할인 뒤)
 * @param shippingFee  배송비 (과세)
 */
record TaxSplit(int taxFree, int taxableGoods, int shippingFee) {

    /** 과세 합계 (상품 + 배송비). */
    int taxable() {
        return taxableGoods + shippingFee;
    }

    static TaxSplit of(Order order, Predicate<OrderItem> isTaxFree) {
        long items = 0;
        long free = 0;
        for (OrderItem it : order.getItems()) {
            items += it.getLineAmount();
            if (isTaxFree.test(it)) {
                free += it.getLineAmount();
            }
        }
        int freeDiscount = items == 0 ? 0 : (int) Math.round((double) order.getDiscountAmount() * free / items);
        int taxFree = (int) free - freeDiscount;
        int shipping = order.getShippingFee();
        return new TaxSplit(taxFree, order.getTotalAmount() - shipping - taxFree, shipping);
    }

    /**
     * 환불액을 이 주문의 면세·과세 비율로 나눈다. 전액 환불이면 결제 때 나눈 그대로 돌려준다.
     *
     * @param total  주문 결제액
     * @param amount 환불액
     */
    Refund refund(int total, int amount) {
        if (amount >= total) {
            return new Refund(taxFree, taxable());
        }
        int free = total == 0 ? 0 : (int) Math.round((double) amount * taxFree / total);
        return new Refund(free, amount - free);
    }

    record Refund(int taxFree, int taxable) {
    }
}
