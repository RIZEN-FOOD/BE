package com.rizenfood.api.order.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자 [매출] — 과세·면세 구분 (2026-10-12). */
public final class SalesDtos {

    private SalesDtos() {
    }

    /**
     * @param truncated 내역이 많아 화면에는 앞부분만 보냈다. 엑셀에는 전부 들어간다
     */
    public record Report(LocalDate from, LocalDate to, Totals totals, List<Line> lines, boolean truncated) {
    }

    /**
     * 기간 합계. 결제는 결제일, 환불은 처리일 기준이다.
     *
     * @param taxableSupply 과세 순매출의 공급가액 (÷1.1)
     * @param taxableVat    과세 순매출의 부가세
     */
    public record Totals(
            int paidCount,
            int taxFreeSales,
            int taxableGoodsSales,
            int shippingFee,
            int discount,
            int refundCount,
            int taxFreeRefund,
            int taxableRefund,
            int taxFreeNet,
            int taxableNet,
            int taxableSupply,
            int taxableVat,
            int totalNet) {
    }

    /**
     * 내역 한 줄. 결제(PAID)는 양수, 환불(REFUND)은 음수다.
     * 환불은 상품·배송비로 나누지 않으므로 taxableGoods·shippingFee 가 비어 있다.
     */
    public record Line(
            String kind,
            Instant at,
            String orderNo,
            String orderStatus,
            int taxFree,
            Integer taxableGoods,
            Integer shippingFee,
            int taxable,
            int total) {
    }
}
