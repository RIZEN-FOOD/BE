package com.rizenfood.api.popup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;

/**
 * 팝업 (실제 DB).
 *
 * - 숨김·기간 밖 팝업은 공개 API 에 나가지 않는다
 * - 로그인하지 않으면 관리 API 를 못 쓴다
 * - 위험한 이동 주소(javascript:, //다른사이트)는 저장되지 않는다
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PopupIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String KEY = "popups/2026/09/0123456789abcdef0123456789abcdef";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    PopupService service;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM popup");
    }

    private PopupDtos.SaveRequest req(String title, boolean alwaysOn, Instant start, Instant end, boolean visible) {
        return new PopupDtos.SaveRequest(title, KEY, true, true, "/products/cream-of-rice",
                alwaysOn, start, end, visible);
    }

    @Test
    @DisplayName("공개 API 에는 지금 떠야 하는 팝업만 순서대로 나간다")
    void publicListFiltersHiddenAndExpired() throws Exception {
        Instant now = Instant.now();
        service.create(req("상시", true, null, null, true));
        service.create(req("숨김", true, null, null, false));
        service.create(req("기간 중", false, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), true));
        service.create(req("지난 행사", false, now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), true));
        service.create(req("예정", false, now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), true));

        mvc.perform(get("/api/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("상시"))
                .andExpect(jsonPath("$[1].title").value("기간 중"))
                .andExpect(jsonPath("$[0].imageUrl").value(org.hamcrest.Matchers.endsWith(KEY + "_large.webp")))
                .andExpect(jsonPath("$[0].linkUrl").value("/products/cream-of-rice"))
                .andExpect(jsonPath("$[0].showLinkButton").value(true));
    }

    @Test
    @DisplayName("로그인하지 않으면 관리 API 는 401")
    void adminApiNeedsLogin() throws Exception {
        mvc.perform(get("/api/admin/popups")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/popups").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("위험한 이동 주소·잘못된 이미지 키·잘못된 기간은 저장되지 않는다")
    void rejectsBadInput() throws Exception {
        Cookie admin = login();
        for (String link : new String[] {"javascript:alert(1)", "//evil.example", "/\\\\evil.example", "data:text/html,x"}) {
            mvc.perform(post("/api/admin/popups").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                            .content(body(KEY, link, true, "null", "null")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/popups").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("../../etc/passwd", "", true, "null", "null")))
                .andExpect(status().isBadRequest());
        // 이동 버튼을 켰는데 주소가 없음
        mvc.perform(post("/api/admin/popups").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEY, "", true, "null", "null").replace("\"showLinkButton\":false", "\"showLinkButton\":true")))
                .andExpect(status().isBadRequest());
        // 종료가 시작보다 앞
        mvc.perform(post("/api/admin/popups").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEY, "", false, "\"2026-10-02T00:00:00Z\"", "\"2026-10-01T00:00:00Z\"")))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM popup", Integer.class)).isZero();

        // 정상 입력은 저장된다
        mvc.perform(post("/api/admin/popups").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEY, "https://smartstore.naver.com/rizenfood", true, "null", "null")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM popup", Integer.class)).isEqualTo(1);
    }

    private static String body(String key, String link, boolean alwaysOn, String start, String end) {
        return """
                {"title":"가을 행사","imageKey":"%s","showHideToday":true,"showLinkButton":false,
                 "linkUrl":"%s","alwaysOn":%s,"startAt":%s,"endAt":%s,"visible":true}
                """.formatted(key, link.replace("\\", "\\\\"), alwaysOn, start, end);
    }

    private Cookie login() throws Exception {
        String username = "p" + System.nanoTime();
        jdbc.update("INSERT INTO admin_user (username, password_hash, display_name, role) VALUES (?, ?, ?, ?)",
                username, encoder.encode("popup2026ok"), "테스트", "ADMIN");
        MockHttpServletResponse res = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"popup2026ok\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        for (String h : res.getHeaders("Set-Cookie")) {
            if (h.startsWith("rizen_admin_token=")) {
                return new Cookie("rizen_admin_token", h.substring("rizen_admin_token=".length(), h.indexOf(';')));
            }
        }
        throw new AssertionError("관리자 쿠키가 없다");
    }
}
