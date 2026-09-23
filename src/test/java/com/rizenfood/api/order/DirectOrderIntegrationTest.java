package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.cart.dto.CartDtos;
import com.rizenfood.api.order.dto.OrderDtos;

/**
 * «바로 구매» — 장바구니를 거치지 않는 주문 (2026-09-23).
 *
 * 장바구니 주문과 같은 파이프라인(재고 원자 차감 → 할인 → 배송비 → 스냅샷)을 타야 하고,
 * 그 위에 두 가지가 더 지켜져야 한다.
 *   - 가격은 요청이 아니라 상품 테이블에서 온다 (요청엔 «무엇을 몇 개»만 있다)
 *   - from_cart=false 로 남는다 — 결제 확정 뒤 장바구니를 비우는 로직이 이 표시를 보고 건너뛴다
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class DirectOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OrderService orderService;

    @Autowired
    JdbcTemplate jdbc;

    private Long insertProduct(String name, int price, Integer discountPrice, int stock, boolean visible) {
        return jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, discount_price, stock, visible) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                "direct-" + System.nanoTime(), name, price, discountPrice, stock, visible);
    }

    private int stockOf(Long id) {
        return jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id);
    }

    private OrderDtos.CreateRequest request(List<OrderDtos.DirectItem> items) {
        return new OrderDtos.CreateRequest(
                "테스트", "010-0000-0002", null,
                "테스트", "010-0000-0002",
                "06236", "서울 강남구", null, null,
                null, items);
    }

    @Test
    @DisplayName("요청엔 상품·수량만 있고, 금액은 상품 테이블(할인가)에서 계산된다")
    void pricesComeFromProductTable() {
        Long id = insertProduct("바로구매 상품", 12_900, 11_900, 10, true);

        OrderDtos.OrderView view = orderService.createDirect(
                null, request(List.of(new OrderDtos.DirectItem(id, null, 3))));

        assertThat(view.itemsAmount()).isEqualTo(11_900 * 3);
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).quantity()).isEqualTo(3);
        assertThat(stockOf(id)).as("재고가 원자적으로 3 빠진다").isEqualTo(7);
    }

    @Test
    @DisplayName("바로구매 주문은 from_cart=false 로 남는다 — 결제 뒤 장바구니를 건드리지 않게")
    void directOrderIsMarkedNotFromCart() {
        Long id = insertProduct("바로구매 상품", 12_900, null, 10, true);

        OrderDtos.OrderView view = orderService.createDirect(
                null, request(List.of(new OrderDtos.DirectItem(id, null, 1))));

        Boolean fromCart = jdbc.queryForObject(
                "SELECT from_cart FROM orders WHERE order_no = ?", Boolean.class, view.orderNo());
        assertThat(fromCart).isFalse();
    }

    @Test
    @DisplayName("재고보다 많이 사려 하면 주문이 만들어지지 않고 재고도 그대로다")
    void rejectsWhenStockIsShort() {
        Long id = insertProduct("바로구매 상품", 12_900, null, 2, true);

        assertThatThrownBy(() -> orderService.createDirect(
                null, request(List.of(new OrderDtos.DirectItem(id, null, 5)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재고가 부족");
        assertThat(stockOf(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("판매하지 않는 상품은 조용히 빼지 않고 이유를 말한다 (장바구니와 다른 점)")
    void rejectsHiddenProductLoudly() {
        Long id = insertProduct("숨긴 상품", 12_900, null, 10, false);

        assertThatThrownBy(() -> orderService.createDirect(
                null, request(List.of(new OrderDtos.DirectItem(id, null, 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("판매하지 않는");
    }

    @Test
    @DisplayName("견적은 못 사는 항목을 빼지 않고 사유를 달아 준다. 금액엔 넣지 않는다")
    void quoteMarksUnavailableWithReason() {
        Long ok = insertProduct("살 수 있는 상품", 10_000, null, 5, true);
        Long soldOut = insertProduct("품절 상품", 10_000, null, 0, true);

        CartDtos.CartView q = orderService.quote(List.of(
                new OrderDtos.DirectItem(ok, null, 2),
                new OrderDtos.DirectItem(soldOut, null, 1)));

        assertThat(q.items()).hasSize(2);
        assertThat(q.items().get(0).available()).isTrue();
        assertThat(q.items().get(1).available()).isFalse();
        assertThat(q.items().get(1).reason()).contains("품절");
        assertThat(q.itemsAmount()).as("품절 항목은 금액에서 빠진다").isEqualTo(20_000);
        assertThat(q.hasUnavailable()).isTrue();
        assertThat(stockOf(ok)).as("견적은 재고를 잡지 않는다").isEqualTo(5);
    }
}
