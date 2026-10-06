package com.rizenfood.api.naverpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 네이버페이 주문형 — 켜져 있을 때 (2026-10-06).
 *
 * 실제 네이버로 나가지 않게 가짜 클라이언트를 끼우고, 우리가 보내는 XML 과 응답 처리를 본다.
 * ★ 가격은 요청이 아니라 DB 에서 온다 (CLAUDE.md 규칙 5).
 * ★ 수동 품절·옵션 상품·수량 초과는 네이버로 보내기 전에 거절한다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false",
        "app.site-url=https://shop.test",
        "app.naverpay.enabled=true",
        "app.naverpay.mode=test",
        "app.naverpay.merchant-id=np_test",
        "app.naverpay.certi-key=CERTI-SECRET",
        "app.naverpay.button-key=BUTTON-KEY",
        "app.naverpay.common-key=s_common"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NaverPayIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 네이버 서버 대신. 마지막으로 받은 XML 을 기억하고 정해 둔 응답을 돌려준다. */
    static class FakeNaverPay implements NaverPayClient {
        String lastUrl;
        String lastXml;
        String nextResponse = "SUCCESS:KEY12345:500001";
        int calls;

        @Override
        public String post(String url, String xml) {
            calls++;
            lastUrl = url;
            lastXml = xml;
            return nextResponse;
        }
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        @Primary
        FakeNaverPay fakeNaverPay() {
            return new FakeNaverPay();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeNaverPay fake;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        fake.lastUrl = null;
        fake.lastXml = null;
        fake.calls = 0;
        fake.nextResponse = "SUCCESS:KEY12345:500001";
    }

    private long product(int price, Integer discount, int stock, boolean soldOut) {
        return jdbc.queryForObject(
                "INSERT INTO product (slug, name_ko, price, discount_price, stock, visible, sold_out) "
                        + "VALUES (?, ?, ?, ?, ?, true, ?) RETURNING id",
                Long.class, "np-" + System.nanoTime(), "네이버 테스트 상품", price, discount, stock, soldOut);
    }

    private String orderBody(long productId, int qty) {
        return "{\"productId\":" + productId + ",\"quantity\":" + qty + "}";
    }

    @Test
    @DisplayName("공개 설정: 켜짐·버튼 키·테스트 SDK 주소만 내보내고 가맹점 인증키는 숨긴다")
    void publicConfig() throws Exception {
        mvc.perform(get("/api/naverpay/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.buttonKey").value("BUTTON-KEY"))
                .andExpect(jsonPath("$.scriptUrl").value("https://test-pay.naver.com/assets/button/latest/npay.button.js"))
                .andExpect(jsonPath("$.commonKey").value("s_common"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("CERTI"))));
    }

    @Test
    @DisplayName("구매 버튼: DB 의 할인가로 주문을 등록하고 네이버가 준 인증키·가맹점번호를 돌려준다")
    void registersWithServerPrice() throws Exception {
        long id = product(12_900, 9_900, 10, false);

        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("KEY12345"))
                .andExpect(jsonPath("$.merchantNo").value("500001"));

        assertThat(fake.lastUrl).isEqualTo("https://test-api.pay.naver.com/o/customer/api/order/v20/register");
        assertThat(fake.lastXml)
                .contains("<merchantId>np_test</merchantId>")
                .contains("<certiKey>CERTI-SECRET</certiKey>")
                .contains("<basePrice>9900</basePrice>")
                .contains("<quantity>2</quantity>")
                .contains("<infoUrl>https://shop.test/products/")
                .contains("<feeType>CONDITIONAL_FREE</feeType>");
    }

    @Test
    @DisplayName("관리자가 수동 품절로 돌린 상품은 재고가 남아 있어도 네이버로 보내지 않는다")
    void rejectsManualSoldOut() throws Exception {
        long id = product(12_900, null, 10, true);

        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("품절되었습니다."));
        assertThat(fake.calls).isZero();
    }

    @Test
    @DisplayName("재고보다 많거나 1,000개 이상이면 거절한다")
    void rejectsBadQuantity() throws Exception {
        long id = product(12_900, null, 3, false);

        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 4)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 1000)))
                .andExpect(status().isBadRequest());
        assertThat(fake.calls).isZero();
    }

    @Test
    @DisplayName("옵션을 고르는 상품은 아직 네이버페이로 받지 않는다")
    void rejectsOptionProduct() throws Exception {
        long id = product(12_900, null, 10, false);
        jdbc.update("INSERT INTO product_option (product_id, name, price_delta, stock, visible, sort_order) "
                + "VALUES (?, '대용량', 3000, 5, true, 0)", id);

        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 1)))
                .andExpect(status().isBadRequest());
        assertThat(fake.calls).isZero();
    }

    @Test
    @DisplayName("네이버가 거절하면 502 와 함께 일반 안내 문구 — 네이버의 사유는 손님에게 보이지 않는다")
    void upstreamFailure() throws Exception {
        long id = product(12_900, null, 10, false);
        fake.nextResponse = "FAIL:[E9999]가맹점 인증 실패";

        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 1)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("NAVERPAY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("E9999"))));
    }

    @Test
    @DisplayName("상품정보: 요청한 번호마다 가격·판매상태·재고를 XML 로, 모르는 번호는 건너뛴다")
    void productInfo() throws Exception {
        long onSale = product(12_900, 9_900, 7, false);
        long soldOut = product(12_900, null, 7, true);

        String xml = mvc.perform(get(URI.create("/api/naverpay/product-info?product%5B0%5D%5Bid%5D=" + onSale
                        + "&product%5B1%5D%5Bid%5D=" + soldOut + "&product%5B2%5D%5Bid%5D=99999999")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(xml).contains("<id>" + onSale + "</id>").contains("<basePrice>9900</basePrice>")
                .contains("<status>ON_SALE</status>").contains("<stockQuantity>7</stockQuantity>")
                .contains("<id>" + soldOut + "</id>").contains("<status>SOLD_OUT</status>")
                .doesNotContain("99999999")
                .doesNotContain("CERTI");
    }

    @Test
    @DisplayName("도서산간비: 제주 우편번호면 정책의 추가 배송비, 내륙이면 0")
    void additionalFee() throws Exception {
        int island = jdbc.queryForObject(
                "SELECT island_extra_fee FROM shipping_policy WHERE visible ORDER BY id LIMIT 1", Integer.class);

        String jeju = mvc.perform(get(URI.create("/api/naverpay/additional-fee?productId%5B0%5D=1&zipcode=63000")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String seoul = mvc.perform(get(URI.create("/api/naverpay/additional-fee?productId%5B0%5D=1&zipcode=06236")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(jeju).contains("<surprice>" + island + "</surprice>");
        assertThat(seoul).contains("<surprice>0</surprice>");
    }
}
