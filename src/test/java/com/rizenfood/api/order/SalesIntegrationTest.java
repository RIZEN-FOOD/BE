package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.common.SimpleXlsxReader;
import com.rizenfood.api.order.dto.ClaimDtos;
import com.rizenfood.api.order.dto.OrderDtos;
import com.rizenfood.api.order.dto.SalesDtos;

/**
 * 관리자 [매출] — 과세·면세 구분 (V40, 2026-10-12).
 *
 * ★ 주문 상품에 면세 여부가 스냅샷으로 남는다.
 * ★ 결제는 결제일 매출, 환불은 처리일에 뺀다. 결제 전 주문은 세지 않는다.
 * ★ 배송비는 과세. 구분 도입 전 주문(스냅샷 없음)은 상품의 지금 설정을 따른다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SalesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OrderService orderService;

    @Autowired
    ClaimService claimService;

    @Autowired
    SalesService salesService;

    @Autowired
    JdbcTemplate jdbc;

    private long product(int price, boolean taxFree) {
        return jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible, tax_free) "
                        + "VALUES (?, ?, ?, 50, true, ?) RETURNING id",
                Long.class, "tax-" + System.nanoTime(), taxFree ? "플레인" : "초코", price, taxFree);
    }

    private String paidOrder(List<OrderDtos.DirectItem> items) {
        String orderNo = orderService.createDirect(null, new OrderDtos.CreateRequest(
                "김라이", "010-1111-2222", null, "김라이", "010-1111-2222",
                "06236", "서울 강남구", null, null, null, items)).orderNo();
        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        return orderNo;
    }

    private static SalesDtos.Line line(SalesDtos.Report r, String orderNo, String kind) {
        return r.lines().stream()
                .filter(l -> l.orderNo().equals(orderNo) && l.kind().equals(kind))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("결제·환불·결제 전 주문·구분 도입 전 주문을 과세·면세로 나눠 합계를 낸다")
    void splitsTaxFreeAndTaxable() {
        long plain = product(10_000, true);
        long choco = product(5_000, false);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // A — 면세 10,000 + 과세 5,000 + 배송비
        String a = paidOrder(List.of(new OrderDtos.DirectItem(plain, null, 1), new OrderDtos.DirectItem(choco, null, 1)));
        assertThat(jdbc.queryForList(
                "SELECT oi.tax_free FROM order_item oi JOIN orders o ON o.id = oi.order_id WHERE o.order_no = ? ORDER BY oi.id",
                Boolean.class, a)).as("면세 여부 스냅샷").containsExactly(true, false);
        int shipA = jdbc.queryForObject("SELECT shipping_fee FROM orders WHERE order_no = ?", Integer.class, a);

        // B — 면세만, 결제 뒤 전액 환불
        String b = paidOrder(List.of(new OrderDtos.DirectItem(plain, null, 1)));
        int totalB = jdbc.queryForObject("SELECT total_amount FROM orders WHERE order_no = ?", Integer.class, b);
        ClaimDtos.View claim = claimService.create(b, null, new ClaimDtos.CreateRequest("CANCEL", "CHANGE_MIND", null));
        claimService.process(claim.id(), new ClaimDtos.ProcessRequest("COMPLETED", null, null));

        // C — 결제 전 주문은 매출이 아니다
        String c = orderService.createDirect(null, new OrderDtos.CreateRequest(
                "김라이", "010-1111-2222", null, "김라이", "010-1111-2222",
                "06236", "서울 강남구", null, null, null,
                List.of(new OrderDtos.DirectItem(plain, null, 3)))).orderNo();

        // D — 구분 도입 전 주문처럼 스냅샷을 비운다 → 상품의 지금 설정(면세)을 따른다
        String d = paidOrder(List.of(new OrderDtos.DirectItem(plain, null, 2)));
        jdbc.update("UPDATE order_item SET tax_free = NULL WHERE order_id = (SELECT id FROM orders WHERE order_no = ?)", d);
        int shipD = jdbc.queryForObject("SELECT shipping_fee FROM orders WHERE order_no = ?", Integer.class, d);

        SalesDtos.Report r = salesService.report(today, today);

        SalesDtos.Line la = line(r, a, "PAID");
        assertThat(la.taxFree()).isEqualTo(10_000);
        assertThat(la.taxableGoods()).isEqualTo(5_000);
        assertThat(la.shippingFee()).isEqualTo(shipA);
        assertThat(la.taxFree() + la.taxable()).isEqualTo(la.total());

        SalesDtos.Line refund = line(r, b, "REFUND");
        assertThat(refund.taxFree()).isEqualTo(-10_000);
        assertThat(refund.total()).isEqualTo(-totalB);

        assertThat(r.lines()).noneMatch(l -> l.orderNo().equals(c));
        assertThat(line(r, d, "PAID").taxFree()).as("스냅샷 없는 옛 주문").isEqualTo(20_000);

        SalesDtos.Totals t = r.totals();
        assertThat(t.paidCount()).isEqualTo(3);
        assertThat(t.refundCount()).isEqualTo(1);
        assertThat(t.taxFreeNet()).isEqualTo(10_000 + 10_000 + 20_000 - 10_000);
        assertThat(t.taxableNet()).isEqualTo(5_000 + shipA + (totalB - 10_000) + shipD - (totalB - 10_000));
        assertThat(t.totalNet()).isEqualTo(t.taxFreeNet() + t.taxableNet());
        assertThat(t.taxableSupply() + t.taxableVat()).isEqualTo(t.taxableNet());

        // 엑셀 — 머리글과 내역이 들어 있다
        List<List<String>> rows = SimpleXlsxReader.read(salesService.export(today, today), 100);
        assertThat(rows.get(0)).contains("면세", "과세 합계");
        assertThat(rows).anyMatch(row -> row.contains(a));
    }

    @Test
    @DisplayName("기간이 거꾸로이거나 1년을 넘으면 거절한다")
    void rejectsBadPeriod() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        assertThatThrownBy(() -> salesService.report(today, today.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> salesService.report(today.minusDays(400), today))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
