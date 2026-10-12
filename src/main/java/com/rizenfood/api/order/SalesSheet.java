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
 * 결제 한 건·환불 한 건이 한 줄이다. 환불은 음수로 적어 열을 더하면 순매출이 된다.
 * 맨 아래에 합계와 과세분 공급가액·부가세를 붙인다.
 */
final class SalesSheet {

    private SalesSheet() {
    }

    static final List<String> HEADERS = List.of(
            "일시", "구분", "주문번호", "주문상태",
            "면세", "과세 상품", "배송비(과세)", "과세 합계", "합계");

    private static final int[] WIDTHS = {17, 8, 22, 11, 12, 12, 12, 12, 12};

    private static final DateTimeFormatter KST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    static byte[] build(SalesDtos.Report report) {
        List<List<Object>> rows = new ArrayList<>();
        for (SalesDtos.Line l : report.lines()) {
            rows.add(Arrays.asList(
                    l.at() != null ? KST.format(l.at()) : null,
                    "PAID".equals(l.kind()) ? "결제" : "환불",
                    l.orderNo(),
                    OrderShippingSheet.STATUS_LABEL.getOrDefault(l.orderStatus(), l.orderStatus()),
                    l.taxFree(),
                    l.taxableGoods(),
                    l.shippingFee(),
                    l.taxable(),
                    l.total()));
        }

        SalesDtos.Totals t = report.totals();
        rows.add(Arrays.asList());
        rows.add(Arrays.asList("기간", report.from() + " ~ " + report.to()));
        rows.add(Arrays.asList("합계(순매출)", null, null, null,
                t.taxFreeNet(), null, null, t.taxableNet(), t.totalNet()));
        rows.add(Arrays.asList("과세 공급가액", null, null, null, null, null, null, t.taxableSupply()));
        rows.add(Arrays.asList("과세 부가세", null, null, null, null, null, null, t.taxableVat()));
        return SimpleXlsxWriter.write("매출", HEADERS, rows, WIDTHS);
    }
}
