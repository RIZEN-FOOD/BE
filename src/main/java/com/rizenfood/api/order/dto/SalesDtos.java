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
     * 기간 합계 (결제일 기준). 취소·환불된 주문은 매출에서 뺀다 (2026-10-13 대표 확인).
     *
     * @param excludedCount  기간 안에 결제됐지만 취소·환불돼 뺀 주문 수
     * @param taxableSupply  과세 매출의 공급가액 (÷1.1)
     * @param taxableVat     과세 매출의 부가세
     */
    public record Totals(
            int orderCount,
            int excludedCount,
            int taxFreeGoods,
            int taxFreeShipping,
            int taxableGoods,
            int taxableShipping,
            int discount,
            int taxFree,
            int taxable,
            int taxableSupply,
            int taxableVat,
            int total) {
    }

    /**
     * 주문 한 건.
     *
     * @param shippingTaxFree 배송비가 면세인가 (면세 상품만 산 주문)
     */
    public record Line(
            Instant paidAt,
            String orderNo,
            String orderStatus,
            int taxFreeGoods,
            int taxableGoods,
            int shippingFee,
            boolean shippingTaxFree,
            int taxFree,
            int taxable,
            int total) {
    }
}
