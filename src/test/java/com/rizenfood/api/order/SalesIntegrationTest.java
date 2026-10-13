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
 * 관리자 [매출] — 과세·면세 구분 (V40, 2026-10-12 / 규칙 보완 2026-10-13).
 *
 * ★ 주문 상품에 면세 여부가 스냅샷으로 남는다.
 * ★ 결제일 매출. 취소·환불된 주문과 결제 전 주문은 매출이 아니다.
 * ★ 배송비는 면세 상품만 산 주문이면 면세, 섞이면 과세.
 * ★ 구분 도입 전 주문(스냅샷 없음)은 상품의 지금 설정을 따른다.
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

    private String newOrder(List<OrderDtos.DirectItem> items) {
        return orderService.createDirect(null, new OrderDtos.CreateRequest(
                "김라이", "010-1111-2222", null, "김라이", "010-1111-2222",
                "06236", "서울 강남구", null, null, null, items)).orderNo();
    }

    private String paidOrder(List<OrderDtos.DirectItem> items) {
        String orderNo = newOrder(items);
        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        return orderNo;
    }

    private int shippingOf(String orderNo) {
        return jdbc.queryForObject("SELECT shipping_fee FROM orders WHERE order_no = ?", Integer.class, orderNo);
    }

    private static SalesDtos.Line line(SalesDtos.Report r, String orderNo) {
        return r.lines().stream().filter(l -> l.orderNo().equals(orderNo)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("섞인 주문·면세만 주문·취소·결제 전·구분 도입 전 주문을 규칙대로 나눈다")
    void splitsTaxFreeAndTaxable() {
        long plain = product(10_000, true);
        long choco = product(5_000, false);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // A — 면세 10,000 + 과세 5,000 → 배송비는 과세
        String a = paidOrder(List.of(new OrderDtos.DirectItem(plain, null, 1), new OrderDtos.DirectItem(choco, null, 1)));
        assertThat(jdbc.queryForList(
                "SELECT oi.tax_free FROM order_item oi JOIN orders o ON o.id = oi.order_id WHERE o.order_no = ? ORDER BY oi.id",
                Boolean.class, a)).as("면세 여부 스냅샷").containsExactly(true, false);

        // B — 면세만 → 배송비도 면세
        String b = paidOrder(List.of(new OrderDtos.DirectItem(plain, null, 1)));

        // C — 결제 뒤 취소(환불) → 매출에서 빠진다
        String c = paidOrder(List.of(new OrderDtos.DirectItem(choco, null, 1)));
        ClaimDtos.View claim = claimService.create(c, null, new ClaimDtos.CreateRequest("CANCEL", "CHANGE_MIND", null));
        claimService.process(claim.id(), new ClaimDtos.ProcessRequest("COMPLETED", null, null));

        // D — 결제 전 주문은 매출이 아니다
        String d = newOrder(List.of(new OrderDtos.DirectItem(plain, null, 3)));

        // E — 구분 도입 전 주문처럼 스냅샷을 비운다 → 상품의 지금 설정(면세)을 따른다
        String e = paidOrder(List.of(new OrderDtos.DirectItem(plain, null, 2)));
        jdbc.update("UPDATE order_item SET tax_free = NULL WHERE order_id = (SELECT id FROM orders WHERE order_no = ?)", e);

        SalesDtos.Report r = salesService.report(today, today);

        SalesDtos.Line la = line(r, a);
        assertThat(la.taxFreeGoods()).isEqualTo(10_000);
        assertThat(la.taxableGoods()).isEqualTo(5_000);
        assertThat(la.shippingTaxFree()).isFalse();
        assertThat(la.taxable()).isEqualTo(5_000 + shippingOf(a));
        assertThat(la.taxFree() + la.taxable()).isEqualTo(la.total());

        SalesDtos.Line lb = line(r, b);
        assertThat(lb.shippingTaxFree()).isTrue();
        assertThat(lb.taxFree()).isEqualTo(10_000 + shippingOf(b));
        assertThat(lb.taxable()).isZero();

        assertThat(r.lines()).as("취소·결제 전 주문은 없다")
                .noneMatch(l -> l.orderNo().equals(c) || l.orderNo().equals(d));
        assertThat(line(r, e).taxFree()).as("스냅샷 없는 옛 주문").isEqualTo(20_000 + shippingOf(e));

        SalesDtos.Totals t = r.totals();
        assertThat(t.orderCount()).isEqualTo(3);
        assertThat(t.excludedCount()).isEqualTo(1);
        assertThat(t.taxFree()).isEqualTo(10_000 + (10_000 + shippingOf(b)) + (20_000 + shippingOf(e)));
        assertThat(t.taxable()).isEqualTo(5_000 + shippingOf(a));
        assertThat(t.total()).isEqualTo(t.taxFree() + t.taxable());
        assertThat(t.taxableSupply() + t.taxableVat()).isEqualTo(t.taxable());

        // 엑셀 — 머리글과 내역이 들어 있고, 취소 주문은 없다
        List<List<String>> rows = SimpleXlsxReader.read(salesService.export(today, today), 100);
        assertThat(rows.get(0)).contains("면세 합계", "과세 합계", "배송비 구분");
        assertThat(rows).anyMatch(row -> row.contains(a));
        assertThat(rows).noneMatch(row -> row.contains(c));
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
