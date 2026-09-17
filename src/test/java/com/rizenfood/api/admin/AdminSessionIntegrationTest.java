package com.rizenfood.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 관리자 로그인이 실제로 끊기는지 (실제 DB).
 *
 * 비밀번호를 바꾸거나 로그아웃하면, 그전에 복사해 둔 쿠키로는 더 이상 들어올 수 없어야 한다.
 * 그리고 일반 관리자는 관리자 관리 화면(계정 목록)에 접근할 수 없어야 한다.
 */
@SpringBootTest(properties = {
        "app.crypto.phone-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "app.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminSessionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    private String createAdmin(String role, String password) {
        String username = "t" + System.nanoTime();
        jdbc.update("INSERT INTO admin_user (username, password_hash, display_name, role) VALUES (?, ?, ?, ?)",
                username, encoder.encode(password), "테스트", role);
        return username;
    }

    private Cookie login(String username, String password) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return adminCookie(res);
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 예전 쿠키는 막히고, 새 쿠키로 계속 쓴다")
    void passwordChangeRevokesOldCookie() throws Exception {
        String username = createAdmin("SUPER_ADMIN", "rizen2026ok");
        Cookie old = login(username, "rizen2026ok");
        mvc.perform(get("/api/admin/auth/me").cookie(old)).andExpect(status().isOk());

        MockHttpServletResponse changed = mvc.perform(patch("/api/admin/auth/password").cookie(old)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"rizen2026ok\",\"newPassword\":\"newpass2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie fresh = adminCookie(changed);

        // 예전 쿠키(다른 기기·탈취본)는 이제 안 된다
        mvc.perform(get("/api/admin/auth/me").cookie(old)).andExpect(status().isUnauthorized());
        // 지금 화면은 새 쿠키로 계속 쓴다
        mvc.perform(get("/api/admin/auth/me").cookie(fresh)).andExpect(status().isOk());
        // 새 비밀번호로 로그인된다
        login(username, "newpass2026");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 바뀌지 않는다")
    void wrongCurrentPasswordKeepsSession() throws Exception {
        String username = createAdmin("ADMIN", "rizen2026ok");
        Cookie cookie = login(username, "rizen2026ok");

        mvc.perform(patch("/api/admin/auth/password").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one1\",\"newPassword\":\"newpass2026\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/auth/me").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃하면 복사해 둔 쿠키도 쓸 수 없다")
    void logoutRevokesCopiedCookie() throws Exception {
        String username = createAdmin("ADMIN", "rizen2026ok");
        Cookie cookie = login(username, "rizen2026ok");

        mvc.perform(post("/api/admin/auth/logout").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 관리자는 관리자 관리(계정 목록)에 들어올 수 없다")
    void onlySuperAdminManagesAccounts() throws Exception {
        String staff = createAdmin("ADMIN", "rizen2026ok");
        String owner = createAdmin("SUPER_ADMIN", "rizen2026ok");

        mvc.perform(get("/api/admin/accounts").cookie(login(staff, "rizen2026ok")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/accounts").cookie(login(owner, "rizen2026ok")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("최고관리자가 직원 계정을 중지하면 그 직원은 즉시 로그아웃된다")
    void disablingKicksOutImmediately() throws Exception {
        String staff = createAdmin("ADMIN", "rizen2026ok");
        String owner = createAdmin("SUPER_ADMIN", "rizen2026ok");
        Cookie staffCookie = login(staff, "rizen2026ok");
        Cookie ownerCookie = login(owner, "rizen2026ok");
        Long staffId = jdbc.queryForObject("SELECT id FROM admin_user WHERE username = ?", Long.class, staff);

        mvc.perform(patch("/api/admin/accounts/" + staffId).cookie(ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"직원\",\"role\":\"ADMIN\",\"enabled\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/orders").cookie(staffCookie)).andExpect(status().isUnauthorized());
    }

    private static Cookie adminCookie(MockHttpServletResponse res) {
        for (String h : res.getHeaders("Set-Cookie")) {
            if (h.startsWith("rizen_admin_token=")) {
                String value = h.substring("rizen_admin_token=".length(), h.indexOf(';'));
                assertThat(value).isNotEmpty();
                return new Cookie("rizen_admin_token", value);
            }
        }
        throw new AssertionError("관리자 쿠키가 없다");
    }
}
