package com.rizenfood.api.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.cart.Cart;
import com.rizenfood.api.cart.CartService;
import com.rizenfood.api.cart.dto.CartDtos;
import com.rizenfood.api.order.OrderService;
import com.rizenfood.api.order.dto.OrderDtos;

/**
 * 상품별 무료배송 토글 (V39, 2026-10-07).
 *
 * ★ 무료배송으로 둔 상품만 담긴 주문은 배송비 0원 — 도서산간이어도.
 * ★ 일반 상품이 섞이면 평소 배송비 그대로다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class FreeShippingProductIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ProductService productService;

    @Autowired
    OrderService orderService;

    @Autowired
    CartService cartService;

    @Autowired
    JdbcTemplate jdbc;

    private long product(int price, boolean freeShipping) {
        return jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible, free_shipping) "
                        + "VALUES (?, ?, ?, 10, true, ?) RETURNING id",
                Long.class, "p-" + System.nanoTime(), "상품", price, freeShipping);
    }

    private OrderDtos.CreateRequest order(String zipcode, List<OrderDtos.DirectItem> items) {
        return new OrderDtos.CreateRequest(
                "테스트", "010-0000-0007", null, "테스트", "010-0000-0007",
                zipcode, "주소", null, null, null, items);
    }

    @Test
    @DisplayName("무료배송 상품만 바로구매하면 배송비 0원 — 견적·주문 모두, 도서산간이어도")
    void directOrderHasNoShipping() {
        long id = product(100, true);
        List<OrderDtos.DirectItem> items = List.of(new OrderDtos.DirectItem(id, null, 1));

        CartDtos.CartView quote = orderService.quote(items);
        assertThat(quote.shippingFee()).isZero();
        assertThat(quote.totalAmount()).isEqualTo(100);
        assertThat(quote.freeShippingRemaining()).isZero();

        String orderNo = orderService.createDirect(null, order("63000", items)).orderNo(); // 제주 우편번호
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT shipping_fee, total_amount FROM orders WHERE order_no = ?", orderNo);
        assertThat(row.get("shipping_fee")).isEqualTo(0);
        assertThat(row.get("total_amount")).isEqualTo(100);
    }

    @Test
    @DisplayName("일반 상품이 섞이면 평소 배송비 그대로")
    void mixedOrderPaysShipping() {
        long free = product(100, true);
        long normal = product(12_900, false);

        String orderNo = orderService.createDirect(null, order("06236", List.of(
                new OrderDtos.DirectItem(free, null, 1),
                new OrderDtos.DirectItem(normal, null, 1)))).orderNo();

        int fee = jdbc.queryForObject("SELECT shipping_fee FROM orders WHERE order_no = ?", Integer.class, orderNo);
        assertThat(fee).isPositive();
    }

    @Test
    @DisplayName("장바구니에 무료배송 상품만 담으면 배송비 0원")
    void cartHasNoShipping() {
        long id = product(100, true);
        Cart cart = cartService.resolveGuestCart(null, "guest-" + System.nanoTime());

        cartService.add(cart.getId(), new CartDtos.AddRequest(id, null, 2));
        CartDtos.CartView view = cartService.view(cart.getId());

        assertThat(view.itemsAmount()).isEqualTo(200);
        assertThat(view.shippingFee()).isZero();
        assertThat(view.totalAmount()).isEqualTo(200);
    }

    @Test
    @DisplayName("상품 상세에 무료배송 여부가 실린다 — 화면이 배송비 안내를 바꾼다")
    void detailCarriesFlag() {
        long id = product(100, true);
        String slug = jdbc.queryForObject("SELECT slug FROM product WHERE id = ?", String.class, id);

        assertThat(productService.getPublic(slug).freeShipping()).isTrue();
    }
}
