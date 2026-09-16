package com.rizenfood.api.member.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;

/**
 * 간편 로그인 전체 흐름을 실제 DB 로 확인한다.
 *
 * 카카오 키가 아직 없으므로 카카오 쪽(KakaoLoginClient)만 가짜로 바꾼다.
 * 우리 코드 — state 검증, 계정 판단, 동의 화면, 가입, 세션 쿠키 — 는 전부 진짜로 돈다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SocialLoginFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    KakaoLoginClient kakao;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    /** 가짜 카카오가 돌려줄 회원 정보 (테스트마다 바꾼다) */
    private final AtomicReference<SocialProfile> nextProfile = new AtomicReference<>();
    /** 가짜 카카오 로그인 화면에 넘어간 state (진짜 카카오라면 콜백에 그대로 돌려준다) */
    private final AtomicReference<String> issuedState = new AtomicReference<>();

    @BeforeEach
    void fakeKakao() {
        when(kakao.provider()).thenReturn("kakao");
        when(kakao.enabled()).thenReturn(true);
        when(kakao.authorizeUrl(anyString(), anyString())).thenAnswer(inv -> {
            issuedState.set(inv.getArgument(0));
            return "https://kauth.kakao.com/oauth/authorize?state=" + inv.getArgument(0);
        });
        when(kakao.fetchProfile(eq("good-code"), anyString(), anyString()))
                .thenAnswer(inv -> nextProfile.get());
    }

    @Test
    @DisplayName("처음 온 사람: 시작 → 콜백 → 동의 화면 → 가입 → 로그인, 다음엔 바로 로그인")
    void newcomerFullFlow() throws Exception {
        String id = "k-" + System.nanoTime();
        nextProfile.set(new SocialProfile("kakao", id, id + "@example.com", true, "카카오손님"));

        // 1) 시작 — 카카오로 보내고 state 쿠키를 심는다
        MockHttpServletResponse start = mvc.perform(get("/api/auth/oauth/kakao/start").param("next", "/cart"))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(start.getRedirectedUrl()).startsWith("https://kauth.kakao.com/");
        Cookie stateCookie = cookie(start, "rizen_oauth_state");
        assertThat(stateCookie).isNotNull();

        // 2) 콜백 — 처음 온 사람이라 동의 화면으로, 가입 티켓을 심는다 (아직 회원 행은 없다)
        MockHttpServletResponse callback = mvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "good-code").param("state", issuedState.get())
                        .cookie(stateCookie))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(callback.getRedirectedUrl()).isEqualTo("/auth/social-signup");
        Cookie ticket = cookie(callback, "rizen_social_signup");
        assertThat(ticket).isNotNull();
        assertThat(memberCount(id)).isZero();

        // 3) 동의 화면이 보여줄 정보
        mvc.perform(get("/api/auth/oauth/pending").cookie(ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("카카오손님"))
                .andExpect(jsonPath("$.email").value(id + "@example.com"));

        // 4) 동의 없이 마무리하면 거부
        mvc.perform(post("/api/auth/oauth/complete").cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeRequired\":false,\"ageOver14\":true,\"agreeMarketing\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(memberCount(id)).isZero();

        // 5) 동의하면 가입 + 로그인 쿠키 + 원래 가려던 화면
        MvcResult done = mvc.perform(post("/api/auth/oauth/complete").cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeRequired\":true,\"ageOver14\":true,\"agreeMarketing\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("/cart"))
                .andReturn();
        assertThat(cookie(done.getResponse(), "rizen_member_token")).isNotNull();
        assertThat(memberCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT provider FROM member WHERE provider_id = ?", String.class, id)).isEqualTo("KAKAO");

        // 6) 다음 방문 — 동의 화면 없이 바로 로그인
        MockHttpServletResponse again = mvc.perform(get("/api/auth/oauth/kakao/start").param("next", "/checkout"))
                .andReturn().getResponse();
        MockHttpServletResponse relogin = mvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "good-code").param("state", issuedState.get())
                        .cookie(cookie(again, "rizen_oauth_state")))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(relogin.getRedirectedUrl()).isEqualTo("/checkout");
        assertThat(cookie(relogin, "rizen_member_token")).isNotNull();
        assertThat(memberCount(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("state 가 다르면(다른 사이트가 만든 요청) 로그인하지 않는다")
    void forgedStateIsRejected() throws Exception {
        nextProfile.set(new SocialProfile("kakao", "k-forged", null, false, "누군가"));
        MockHttpServletResponse start = mvc.perform(get("/api/auth/oauth/kakao/start"))
                .andReturn().getResponse();

        MockHttpServletResponse forged = mvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "good-code").param("state", "attacker-state")
                        .cookie(cookie(start, "rizen_oauth_state")))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(forged.getRedirectedUrl()).isEqualTo("/auth/login?social_error=expired");
        assertThat(cookie(forged, "rizen_member_token")).isNull();
        assertThat(cookie(forged, "rizen_social_signup")).isNull();

        // state 쿠키 없이 온 콜백도 마찬가지
        mvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "good-code").param("state", issuedState.get()))
                .andExpect(status().isFound())
                .andExpect(r -> assertThat(r.getResponse().getRedirectedUrl())
                        .isEqualTo("/auth/login?social_error=expired"));
    }

    @Test
    @DisplayName("같은 이메일의 이메일 가입 계정이 있으면 가입시키지 않고 기존 방식 안내")
    void emailOfLocalAccountIsNotTakenOver() throws Exception {
        String email = "owner" + System.nanoTime() + "@example.com";
        jdbc.update("INSERT INTO member (email, name, provider, password_hash) VALUES (?, ?, 'LOCAL', 'x')",
                email, "원래 주인");
        nextProfile.set(new SocialProfile("kakao", "k-" + System.nanoTime(), email, true, "다른 사람"));

        MockHttpServletResponse start = mvc.perform(get("/api/auth/oauth/kakao/start")).andReturn().getResponse();
        MockHttpServletResponse callback = mvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "good-code").param("state", issuedState.get())
                        .cookie(cookie(start, "rizen_oauth_state")))
                .andExpect(status().isFound()).andReturn().getResponse();

        assertThat(callback.getRedirectedUrl())
                .isEqualTo("/auth/login?social_error=email_exists&existing=local");
        assertThat(cookie(callback, "rizen_member_token")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("손님이 카카오 화면에서 취소하면 로그인 화면으로 돌아간다")
    void cancelledAtProvider() throws Exception {
        mvc.perform(get("/api/auth/oauth/kakao/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(r -> assertThat(r.getResponse().getRedirectedUrl())
                        .isEqualTo("/auth/login?social_error=cancelled"));
    }

    @Test
    @DisplayName("키가 없는 제공자(네이버)는 목록에서 꺼져 있고 시작도 막힌다")
    void disabledProvider() throws Exception {
        mvc.perform(get("/api/auth/oauth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakao").value(true))
                .andExpect(jsonPath("$.naver").value(false));

        mvc.perform(get("/api/auth/oauth/naver/start"))
                .andExpect(status().isFound())
                .andExpect(r -> assertThat(r.getResponse().getRedirectedUrl())
                        .isEqualTo("/auth/login?social_error=unavailable_provider"));
    }

    @Test
    @DisplayName("가입 티켓 없이 마무리를 부르면 거부")
    void completeWithoutTicket() throws Exception {
        mvc.perform(post("/api/auth/oauth/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeRequired\":true,\"ageOver14\":true,\"agreeMarketing\":false}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────

    private int memberCount(String providerId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM member WHERE provider_id = ?", Integer.class, providerId);
        return n == null ? 0 : n;
    }

    /** Set-Cookie 헤더에서 값이 있는 쿠키를 꺼낸다 (만료용 빈 쿠키는 없는 것으로 본다). */
    private static Cookie cookie(MockHttpServletResponse response, String name) {
        List<String> headers = response.getHeaders("Set-Cookie");
        for (String h : headers) {
            if (h.startsWith(name + "=")) {
                String value = h.substring(name.length() + 1, h.indexOf(';') < 0 ? h.length() : h.indexOf(';'));
                if (!value.isEmpty()) {
                    return new Cookie(name, value);
                }
            }
        }
        return null;
    }
}
