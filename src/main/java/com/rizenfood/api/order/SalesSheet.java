package com.rizenfood.api.order;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.rizenfood.api.common.SimpleXlsxWriter;
import com.rizenfood.api.order.dto.SalesDtos;

/**
 * 매출(과세·면세) 엑셀 — 세무사에게 그대로 넘길 수 있게 (2026-10-12).
 *
 * 주문 한 건이 한 줄이다. 취소·환불된 주문은 들어가지 않는다.
 * 맨 아래에 합계와 과세분 공급가액·부가세를 붙인다.
 */
final class SalesSheet {

    private SalesSheet() {
    }

    static final List<String> HEADERS = List.of(
            "결제일시", "주문번호", "주문상태",
            "면세 상품", "과세 상품", "배송비", "배송비 구분",
            "면세 합계", "과세 합계", "합계");

    private static final int[] WIDTHS = {17, 22, 11, 12, 12, 10, 10, 12, 12, 12};

    private static final DateTimeFormatter KST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    static byte[] build(SalesDtos.Report report) {
        List<List<Object>> rows = new ArrayList<>();
        for (SalesDtos.Line l : report.lines()) {
            rows.add(Arrays.asList(
                    l.paidAt() != null ? KST.format(l.paidAt()) : null,
                    l.orderNo(),
                    OrderShippingSheet.STATUS_LABEL.getOrDefault(l.orderStatus(), l.orderStatus()),
                    l.taxFreeGoods(),
                    l.taxableGoods(),
                    l.shippingFee(),
                    l.shippingFee() == 0 ? null : (l.shippingTaxFree() ? "면세" : "과세"),
                    l.taxFree(),
                    l.taxable(),
                    l.total()));
        }

        SalesDtos.Totals t = report.totals();
        rows.add(Arrays.asList());
        rows.add(Arrays.asList("기간", report.from() + " ~ " + report.to()));
        rows.add(Arrays.asList("합계", t.orderCount() + "건", null,
                t.taxFreeGoods(), t.taxableGoods(), t.taxFreeShipping() + t.taxableShipping(), null,
                t.taxFree(), t.taxable(), t.total()));
        rows.add(Arrays.asList("과세 공급가액", null, null, null, null, null, null, null, t.taxableSupply()));
        rows.add(Arrays.asList("과세 부가세", null, null, null, null, null, null, null, t.taxableVat()));
        if (t.excludedCount() > 0) {
            rows.add(Arrays.asList("취소·환불 제외", t.excludedCount() + "건"));
        }
        return SimpleXlsxWriter.write("매출", HEADERS, rows, WIDTHS);
    }
}
