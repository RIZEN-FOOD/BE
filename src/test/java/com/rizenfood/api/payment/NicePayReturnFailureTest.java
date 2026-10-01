package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.order.OrderService;
import com.rizenfood.api.order.dto.OrderDtos;

/**
 * 나이스가 인증 단계에서 거절했을 때 (2026-10-01).
 *
 * 2026-09-26 운영에서 계좌이체가 나이스에 열려 있지 않아 "N003 결제수단이 유효하지 않습니다"로
 * 거절됐는데, 관리자 화면엔 FAILED 만 보여서 원인을 나이스 콘솔을 뒤져서야 찾았다.
 * 이제 서명이 맞는 거절은 그 사유를 결제에 남기고 주문을 바로 정리한다.
 *
 * ★ 결과 수신 주소는 로그인 없이 열려 있다. 서명이 틀린 거절로 남의 주문이 취소되면 안 된다.
 * ★ 같은 거절이 두 번 와도 재고를 두 번 돌려주면 안 된다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false",
        "app.payment.provider=nicepay",
        "app.payment.nicepay.client-id=" + NicePayReturnFailureTest.CLIENT_ID,
        "app.payment.nicepay.secret-key=" + NicePayReturnFailureTest.SECRET
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NicePayReturnFailureTest {

    static final String CLIENT_ID = "R2_test_client";
    static final String SECRET = "test-secret";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderService orderService;

    @Autowired
    JdbcTemplate jdbc;

    private Long insertProduct(int stock) {
        return jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible) VALUES (?, ?, ?, ?, true) RETURNING id",
                Long.class, "pgfail-" + System.nanoTime(), "거절 테스트 상품", 19_900, stock);
    }

    private int stockOf(Long id) {
        return jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id);
    }

    private String orderStatus(String orderNo) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE order_no = ?", String.class, orderNo);
    }

    private String[] payment(String orderNo) {
        return jdbc.queryForObject(
                "SELECT p.status, p.fail_reason FROM payment p JOIN orders o ON o.id = p.order_id WHERE o.order_no = ?",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, orderNo);
    }

    /** 결제대기 주문 하나를 만든다(바로 구매 2개). */
    private OrderDtos.OrderView pendingOrder(Long productId) {
        return orderService.createDirect(null, new OrderDtos.CreateRequest(
                "테스트", "010-0000-0003", null,
                "테스트", "010-0000-0003",
                "06236", "서울 강남구", null, null,
                null, List.of(new OrderDtos.DirectItem(productId, null, 2))));
    }

    /** 나이스가 보내는 거절 결과. sign=true 면 우리 비밀키로 진짜 서명을 붙인다. */
    private void postRejection(String orderNo, int amount, boolean sign) throws Exception {
        String authToken = "NICEUNTT-test-" + System.nanoTime();
        String amt = String.valueOf(amount);
        String signature = sign
                ? NicePayGateway.sha256Hex(authToken + CLIENT_ID + amt + SECRET)
                : "0000000000000000000000000000000000000000000000000000000000000000";
        mvc.perform(post("/api/payment/nicepay/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("authResultCode", "N003")
                        .param("authResultMsg", "결제수단이 유효하지 않습니다.")
                        .param("orderId", orderNo)
                        .param("amount", amt)
                        .param("authToken", authToken)
                        .param("signature", signature))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("fail=")));
    }

    @Test
    @DisplayName("서명이 맞는 거절이면 주문을 정리하고, 나이스가 보낸 사유를 결제에 남긴다")
    void signedRejectionReleasesOrderAndKeepsReason() throws Exception {
        Long id = insertProduct(10);
        OrderDtos.OrderView order = pendingOrder(id);
        assertThat(stockOf(id)).isEqualTo(8);

        postRejection(order.orderNo(), order.totalAmount(), true);

        assertThat(orderStatus(order.orderNo())).isEqualTo("CANCELLED");
        assertThat(stockOf(id)).as("잡아둔 재고 2개가 돌아온다").isEqualTo(10);
        String[] pay = payment(order.orderNo());
        assertThat(pay[0]).isEqualTo("FAILED");
        assertThat(pay[1]).isEqualTo("나이스 N003: 결제수단이 유효하지 않습니다.");
    }

    @Test
    @DisplayName("서명이 틀린 거절은 주문을 건드리지 않는다 — 주문번호만으로 남의 주문을 취소시킬 수 없다")
    void unsignedRejectionDoesNotTouchOrder() throws Exception {
        Long id = insertProduct(10);
        OrderDtos.OrderView order = pendingOrder(id);

        postRejection(order.orderNo(), order.totalAmount(), false);

        assertThat(orderStatus(order.orderNo())).isEqualTo("PENDING");
        assertThat(stockOf(id)).isEqualTo(8);
        assertThat(payment(order.orderNo())[1]).isNull();
    }

    @Test
    @DisplayName("같은 거절이 두 번 와도 재고는 한 번만 돌아온다")
    void repeatedRejectionRestocksOnce() throws Exception {
        Long id = insertProduct(10);
        OrderDtos.OrderView order = pendingOrder(id);

        postRejection(order.orderNo(), order.totalAmount(), true);
        postRejection(order.orderNo(), order.totalAmount(), true);

        assertThat(stockOf(id)).as("두 번 돌려주면 12 가 된다").isEqualTo(10);
        assertThat(orderStatus(order.orderNo())).isEqualTo("CANCELLED");
    }
}
