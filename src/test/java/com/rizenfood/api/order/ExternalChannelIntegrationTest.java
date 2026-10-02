package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.order.dto.OrderDtos;
import com.rizenfood.api.setting.SiteSettingService;
import com.rizenfood.api.shipping.ShippingPolicy;
import com.rizenfood.api.shipping.ShippingPolicyRepository;

/**
 * 바깥 판매 경로 · 반품/교환 배송비 · 반품지 우편번호 (V36, 2026-10-02).
 *
 * 네이버페이 주문형·톡체크아웃 주문을 같은 주문 테이블에 받기 위한 칸들이다.
 * ★ 같은 경로의 같은 바깥 주문은 한 번만 들어가야 한다 — 주문 동기화가 재시도돼도 두 번 생기면 안 된다.
 * ★ 지금까지의 주문은 전부 자사몰로 남아야 한다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ExternalChannelIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OrderService orderService;

    @Autowired
    ShippingPolicyRepository shippingPolicyRepository;

    @Autowired
    SiteSettingService siteSettingService;

    @Autowired
    JdbcTemplate jdbc;

    /** JDBC 로 바꾼 값과 JPA 캐시를 맞춘다(한 트랜잭션 안에서 섞어 쓰므로). */
    @Autowired
    EntityManager em;

    private String newOrder() {
        Long productId = jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, stock, visible) VALUES (?, ?, ?, ?, true) RETURNING id",
                Long.class, "channel-" + System.nanoTime(), "경로 테스트 상품", 12_900, 10);
        return orderService.createDirect(null, new OrderDtos.CreateRequest(
                "테스트", "010-0000-0004", null,
                "테스트", "010-0000-0004",
                "06236", "서울 강남구", null, null,
                null, List.of(new OrderDtos.DirectItem(productId, null, 1)))).orderNo();
    }

    @Test
    @DisplayName("자사몰 주문은 판매 경로 MALL, 바깥 주문번호 없음 — 관리자 상세에도 그대로 나온다")
    void mallOrderDefaults() {
        String orderNo = newOrder();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT channel, external_order_no FROM orders WHERE order_no = ?", orderNo);
        assertThat(row.get("channel")).isEqualTo("MALL");
        assertThat(row.get("external_order_no")).isNull();

        var detail = orderService.adminGet(orderNo);
        assertThat(detail.channel()).isEqualTo("MALL");
        assertThat(detail.externalOrderNo()).isNull();
    }

    @Test
    @DisplayName("같은 경로의 같은 바깥 주문번호는 두 번 들어갈 수 없다")
    void sameExternalOrderTwiceIsRejected() {
        String a = newOrder();
        String b = newOrder();
        jdbc.update("UPDATE orders SET channel = 'NAVERPAY', external_order_no = 'NP-1001' WHERE order_no = ?", a);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE orders SET channel = 'NAVERPAY', external_order_no = 'NP-1001' WHERE order_no = ?", b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("경로가 다르면 바깥 주문번호가 같아도 된다 — 네이버와 카카오 번호 체계는 따로다")
    void sameNumberOnDifferentChannelsIsAllowed() {
        String a = newOrder();
        String b = newOrder();
        jdbc.update("UPDATE orders SET channel = 'NAVERPAY', external_order_no = 'X-1' WHERE order_no = ?", a);
        jdbc.update("UPDATE orders SET channel = 'KAKAO_CHECKOUT', external_order_no = 'X-1' WHERE order_no = ?", b);
        em.clear(); // 위에서 만든 주문이 JPA 캐시에 남아 있어, 비우지 않으면 바꾸기 전 값을 읽는다

        assertThat(orderService.adminGet(b).channel()).isEqualTo("KAKAO_CHECKOUT");
        assertThat(orderService.adminGet(b).externalOrderNo()).isEqualTo("X-1");
    }

    @Test
    @DisplayName("정해진 판매 경로 말고는 넣을 수 없다")
    void unknownChannelIsRejected() {
        String a = newOrder();

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET channel = 'COUPANG' WHERE order_no = ?", a))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("반품·교환 배송비를 저장하고 읽는다 — 비워 두면 '아직 정하지 않음'으로 남는다")
    void returnAndExchangeFees() {
        ShippingPolicy p = shippingPolicyRepository.findFirstByVisibleTrueOrderByIdAsc().orElseThrow();
        assertThat(p.getReturnFee()).as("처음엔 정하지 않은 상태").isNull();

        p.update(p.getName(), p.getBaseFee(), p.getFreeThreshold(), p.getIslandExtraFee(), 3_500, 7_000);
        shippingPolicyRepository.saveAndFlush(p);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT return_fee, exchange_fee FROM shipping_policy WHERE id = ?", p.getId());
        assertThat(row.get("return_fee")).isEqualTo(3_500);
        assertThat(row.get("exchange_fee")).isEqualTo(7_000);
    }

    @Test
    @DisplayName("반품·교환 배송비는 음수로 저장되지 않는다")
    void negativeFeeIsRejected() {
        Long id = shippingPolicyRepository.findFirstByVisibleTrueOrderByIdAsc().orElseThrow().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE shipping_policy SET return_fee = -1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("반품지 우편번호는 숫자 5자리로 정리해 저장하고, 형식이 틀리면 거절한다")
    void returnZipcode() {
        siteSettingService.updateValues(Map.of("shipping.return_zipcode", " 123-45 "));
        em.flush(); // 서비스가 바꾼 값을 DB 로 내보낸 뒤 JDBC 로 읽는다
        assertThat(jdbc.queryForObject(
                "SELECT value FROM site_setting WHERE key = 'shipping.return_zipcode'", String.class))
                .isEqualTo("12345");

        assertThatThrownBy(() -> siteSettingService.updateValues(Map.of("shipping.return_zipcode", "1234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("숫자 5자리");

        siteSettingService.updateValues(Map.of("shipping.return_zipcode", ""));
        em.flush();
        assertThat(jdbc.queryForObject(
                "SELECT value FROM site_setting WHERE key = 'shipping.return_zipcode'", String.class))
                .as("비우면 빈 값").isEmpty();
    }
}
