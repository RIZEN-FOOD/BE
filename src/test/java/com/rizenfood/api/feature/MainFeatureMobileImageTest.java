package com.rizenfood.api.feature;

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

import com.rizenfood.api.feature.dto.MainFeatureDtos;

/**
 * 메인 FEATURES 모바일 전용 사진 (실제 DB).
 *
 * 올리면 모바일 URL 이 따로 나가고, 올리지 않으면 null 이라 화면이 PC 사진을 그대로 쓴다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class MainFeatureMobileImageTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PC_KEY = "main/2026/09/0123456789abcdef0123456789abcdef";
    private static final String MO_KEY = "main/2026/09/fedcba9876543210fedcba9876543210";

    @Autowired
    MainFeatureService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM main_feature");
    }

    @Test
    @DisplayName("모바일 사진을 올리면 따로 나가고, 없으면 null 이라 PC 사진을 쓴다")
    void mobileImageIsOptional() {
        service.create(new MainFeatureDtos.SaveRequest("모바일 사진 있음", "본문", PC_KEY, MO_KEY, "설명", false, true));
        service.create(new MainFeatureDtos.SaveRequest("모바일 사진 없음", "본문", PC_KEY, null, "설명", false, true));

        List<MainFeatureDtos.PublicItem> items = service.listPublic();
        assertThat(items).hasSize(2);

        assertThat(items.get(0).imageUrl()).endsWith(PC_KEY + "_large.webp");
        assertThat(items.get(0).imageMobileUrl()).endsWith(MO_KEY + "_large.webp");

        assertThat(items.get(1).imageUrl()).endsWith(PC_KEY + "_large.webp");
        assertThat(items.get(1).imageMobileUrl()).isNull();
    }

    @Test
    @DisplayName("수정에서 모바일 사진을 지우면 다시 PC 사진만 쓴다")
    void mobileImageCanBeCleared() {
        Long id = service.create(
                new MainFeatureDtos.SaveRequest("지울 칸", "본문", PC_KEY, MO_KEY, "설명", false, true));
        assertThat(service.listForAdmin().get(0).imageMobileKey()).isEqualTo(MO_KEY);

        service.update(id, new MainFeatureDtos.SaveRequest("지울 칸", "본문", PC_KEY, "", "설명", false, true));

        assertThat(service.listForAdmin().get(0).imageMobileKey()).isNull();
        assertThat(service.listPublic().get(0).imageMobileUrl()).isNull();
    }
}
