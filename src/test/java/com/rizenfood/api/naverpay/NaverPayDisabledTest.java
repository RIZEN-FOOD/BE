package com.rizenfood.api.naverpay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 네이버페이 주문형 — 꺼져 있을 때 (기본값).
 *
 * 가맹 승인 전 운영 서버의 상태다. 버튼은 안 뜨고(config enabled=false), 나머지 주소는 404 여야 한다.
 * 키를 넣었어도 enabled=false 면 마찬가지다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false",
        "app.site-url=https://shop.test",
        "app.naverpay.enabled=false",
        "app.naverpay.merchant-id=np_test",
        "app.naverpay.certi-key=CERTI-SECRET",
        "app.naverpay.button-key=BUTTON-KEY"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NaverPayDisabledTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("꺼져 있으면 버튼을 띄우지 않고, 주문·상품정보·도서산간 주소는 404")
    void everythingHiddenWhenOff() throws Exception {
        mvc.perform(get("/api/naverpay/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.buttonKey").doesNotExist());

        mvc.perform(post("/api/naverpay/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(URI.create("/api/naverpay/product-info?product%5B0%5D%5Bid%5D=1")))
                .andExpect(status().isNotFound());
        mvc.perform(get(URI.create("/api/naverpay/additional-fee?productId%5B0%5D=1&zipcode=63000")))
                .andExpect(status().isNotFound());
    }
}
