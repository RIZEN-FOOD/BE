package com.rizenfood.api.banner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rizenfood.api.banner.dto.HeroImageSlideDtos;

/**
 * 메인 히어로 — 이미지 한 장 슬라이드 (V41, 2026-10-13).
 *
 * ★ 노출 켠 것만 순서대로 메인에 나간다.
 * ★ 버튼은 문구·주소가 둘 다 있을 때만. 모바일 사진을 비우면 비어서 나간다(화면이 PC 사진을 쓴다).
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class HeroImageSlideIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    HeroImageSlideService service;

    @Test
    @DisplayName("노출 켠 슬라이드만 순서대로, 버튼은 문구·주소가 다 있을 때만")
    void publicListOnlyEnabledInOrder() {
        Long event = service.create(new HeroImageSlideDtos.SaveRequest(
                "banners/2026/10/event", "banners/2026/10/event-m", "가을 기획전", "기획전 보기", "/products",
                false, 5, true));
        Long noButton = service.create(new HeroImageSlideDtos.SaveRequest(
                "banners/2026/10/mood", " ", "분위기 사진", "자세히 보기", "", false, 1, true));
        service.create(new HeroImageSlideDtos.SaveRequest(
                "banners/2026/10/hidden", null, "숨긴 배너", null, null, false, 0, false));

        List<HeroImageSlideDtos.PublicItem> list = service.listPublic();

        assertThat(list).extracting(HeroImageSlideDtos.PublicItem::id).containsExactly(noButton, event);
        HeroImageSlideDtos.PublicItem first = list.get(0);
        assertThat(first.buttonLabel()).as("주소가 없으면 버튼도 없다").isNull();
        assertThat(first.imageMobileUrl()).as("모바일 사진을 비움").isNull();
        assertThat(first.imagePcUrl()).endsWith("_large.webp");

        HeroImageSlideDtos.PublicItem second = list.get(1);
        assertThat(second.buttonLabel()).isEqualTo("기획전 보기");
        assertThat(second.linkUrl()).isEqualTo("/products");
        assertThat(second.sortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("노출 끄기·삭제")
    void toggleAndDelete() {
        Long id = service.create(new HeroImageSlideDtos.SaveRequest(
                "banners/2026/10/x", null, "설명", null, null, false, 9, true));

        service.setEnabled(id, false);
        assertThat(service.get(id).enabled()).isFalse();
        assertThat(service.listPublic()).noneMatch(s -> s.id().equals(id));

        service.delete(id);
        assertThat(service.listForAdmin()).noneMatch(s -> s.id().equals(id));
    }
}
