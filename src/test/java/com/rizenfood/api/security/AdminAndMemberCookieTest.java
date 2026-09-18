package com.rizenfood.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 한 브라우저에 관리자 쿠키와 회원 쿠키가 같이 있을 때 (실제 DB).
 *
 * 대표가 관리자에 로그인한 채로 쇼핑몰에서 간편 로그인을 하면 두 쿠키가 함께 남는다.
 * 이때 회원 API 는 회원으로, 관리 API 는 관리자로 동작해야 한다.
 * (2026-09-18: 관리자 쿠키가 있으면 /api/auth/me 가 403 이 되어 마이페이지가 로그인 화면으로 튕겼다.)
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminAndMemberCookieTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Test
    @DisplayName("관리자로 로그인한 브라우저에서도 회원 마이페이지가 열린다")
    void memberApiWorksWhileAdminCookieExists() throws Exception {
        Cookie adminCookie = loginAsAdmin();
        Cookie memberCookie = signUpMember();

        // 회원 API — 두 쿠키가 같이 가도 회원으로 본다
        mvc.perform(get("/api/auth/me").cookie(memberCookie, adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("세션테스트"));

        // 관리 API — 두 쿠키가 같이 가도 관리자로 본다
        mvc.perform(get("/api/admin/auth/me").cookie(memberCookie, adminCookie))
                .andExpect(status().isOk());

        // 회원 쿠키만 있으면 관리 API 는 여전히 막힌다 (회원으로 인증되지만 권한이 없어 403)
        mvc.perform(get("/api/admin/auth/me").cookie(memberCookie))
                .andExpect(status().isForbidden());
    }

    private Cookie loginAsAdmin() throws Exception {
        String username = "a" + System.nanoTime();
        jdbc.update("INSERT INTO admin_user (username, password_hash, display_name, role) VALUES (?, ?, ?, ?)",
                username, encoder.encode("adminpass2026"), "관리자", "SUPER_ADMIN");
        MockHttpServletResponse res = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"adminpass2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return cookie(res, "rizen_admin_token");
    }

    private Cookie signUpMember() throws Exception {
        String email = "m" + System.nanoTime() + "@example.com";
        String body = """
                {"email":"%s","password":"memberpass2026","name":"세션테스트",
                 "ageOver14":true,"agreeRequired":true,"agreeMarketing":false}
                """.formatted(email);
        MockHttpServletResponse res = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return cookie(res, "rizen_member_token");
    }

    private static Cookie cookie(MockHttpServletResponse res, String name) {
        for (String h : res.getHeaders("Set-Cookie")) {
            if (h.startsWith(name + "=")) {
                return new Cookie(name, h.substring(name.length() + 1, h.indexOf(';')));
            }
        }
        throw new AssertionError(name + " 쿠키가 없다");
    }
}
