package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.order.dto.OrderDtos;

/**
 * 출고용 엑셀 — 같은 주문을 대행사에 두 번 넘기지 않는다 (2026-10-06).
 *
 * ★ 기본으로 받으면 '결제 완료' 주문만 담고, 담긴 주문은 '상품 준비중'으로 바뀐다.
 * ★ 그래서 송장을 올리기 전에 한 번 더 받아도 이미 넘긴 주문은 다시 들어가지 않는다.
 * ★ 다시 받아야 하면 '상품 준비중'을 골라 받는다 — 이때는 상태를 바꾸지 않는다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ShippingExportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OrderService orderService;

    @Autowired
    JdbcTemplate jdbc;

    /** 테스트끼리 섞이지 않게, 앞 테스트가 남긴 출고 대기 주문을 치운다. */
    @BeforeEach
    void clearWaiting() {
        jdbc.update("UPDATE orders SET status = 'DELIVERED' WHERE status IN ('PAID', 'PREPARING')");
    }

    private String paidOrder() {
        Long productId = jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible) VALUES (?, ?, ?, ?, true) RETURNING id",
                Long.class, "ship-" + System.nanoTime(), "크림오브라이스", 12_900, 10);
        String orderNo = orderService.createDirect(null, new OrderDtos.CreateRequest(
                "김라이", "010-1234-5678", null, "이즌", "010-9999-8888",
                "06236", "서울 강남구", null, null, null,
                List.of(new OrderDtos.DirectItem(productId, null, 1)))).orderNo();
        orderService.pay(orderNo, null, new OrderDtos.PayRequest("card"), null);
        return orderNo;
    }

    private String status(String orderNo) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE order_no = ?", String.class, orderNo);
    }

    @Test
    @DisplayName("기본으로 받으면 결제 완료 주문을 담고 상품 준비중으로 바꾼다 — 한 번 더 받으면 비어 있다")
    void exportMarksPreparing() {
        String a = paidOrder();
        String b = paidOrder();

        OrderService.ShippingExport first = orderService.adminExportForShipping(null);

        assertThat(first.orderCount()).isEqualTo(2);
        assertThat(first.preparedCount()).isEqualTo(2);
        assertThat(status(a)).isEqualTo("PREPARING");
        assertThat(status(b)).isEqualTo("PREPARING");

        OrderService.ShippingExport again = orderService.adminExportForShipping(null);
        assertThat(again.orderCount()).as("이미 넘긴 주문은 다시 들어가지 않는다").isZero();
    }

    @Test
    @DisplayName("상품 준비중을 골라 받으면 다시 받을 수 있고, 상태는 바꾸지 않는다")
    void reDownloadPreparing() {
        String a = paidOrder();
        orderService.adminExportForShipping(null);

        OrderService.ShippingExport re = orderService.adminExportForShipping("PREPARING");

        assertThat(re.orderCount()).isEqualTo(1);
        assertThat(re.preparedCount()).isZero();
        assertThat(status(a)).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("엑셀을 받은 뒤 들어온 주문만 다음 엑셀에 담긴다")
    void onlyNewOrdersNextTime() {
        paidOrder();
        orderService.adminExportForShipping(null);
        String later = paidOrder();

        OrderService.ShippingExport next = orderService.adminExportForShipping(null);

        assertThat(next.orderCount()).isEqualTo(1);
        assertThat(status(later)).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("송장을 넣어 배송중이 된 주문은 담지 않는다")
    void shippedNotIncluded() {
        String a = paidOrder();
        orderService.adminShip(a, "롯데택배", "123456789012");

        assertThat(orderService.adminExportForShipping(null).orderCount()).isZero();
        assertThat(status(a)).isEqualTo("SHIPPED");
    }
}