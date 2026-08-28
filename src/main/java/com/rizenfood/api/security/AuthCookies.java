package com.rizenfood.api.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 인증 쿠키를 만들고 읽는다.
 *
 * HttpOnly  — 자바스크립트가 읽지 못한다. XSS 가 나도 토큰이 빠져나가지 않는다.
 * Secure    — HTTPS 로만 전송한다. 운영에서는 반드시 켠다.
 * SameSite  — 다른 사이트에서 자동으로 실려 나가는 것을 막는다 (CSRF 방어).
 * Path      — 관리자 쿠키는 관리자 경로에만 실린다.
 */
@Component
public class AuthCookies {

    public static final String ADMIN_TOKEN = "rizen_admin_token";
    public static final String MEMBER_ACCESS = "rizen_member_token";
    public static final String MEMBER_REFRESH = "rizen_member_refresh";
    private static final String ADMIN_PATH = "/";
    /** 리프레시 토큰은 재발급 엔드포인트에만 실리게 경로를 좁힌다. */
    private static final String REFRESH_PATH = "/api/auth";

    private final JwtProperties properties;

    public AuthCookies(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie adminToken(String token, long maxAgeSeconds) {
        return ResponseCookie.from(ADMIN_TOKEN, token)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite(properties.sameSite())
                .path(ADMIN_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** 로그아웃. 같은 속성으로 빈 값에 만료 0 을 줘야 실제로 지워진다. */
    public ResponseCookie expiredAdminToken() {
        return adminToken("", 0);
    }

    public String readAdminToken(HttpServletRequest request) {
        return read(request, ADMIN_TOKEN);
    }

    // ── 회원 쿠키 ──────────────────────────────────────────────

    public ResponseCookie memberAccess(String token, long maxAgeSeconds) {
        return ResponseCookie.from(MEMBER_ACCESS, token)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite(properties.sameSite())
                .path(ADMIN_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    public ResponseCookie memberRefresh(String token, long maxAgeSeconds) {
        return ResponseCookie.from(MEMBER_REFRESH, token)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite(properties.sameSite())
                .path(REFRESH_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** 로그아웃. 두 쿠키를 같은 속성으로 만료시킨다. */
    public ResponseCookie expiredMemberAccess() {
        return memberAccess("", 0);
    }

    public ResponseCookie expiredMemberRefresh() {
        return memberRefresh("", 0);
    }

    public String readMemberAccess(HttpServletRequest request) {
        return read(request, MEMBER_ACCESS);
    }

    public String readMemberRefresh(HttpServletRequest request) {
        return read(request, MEMBER_REFRESH);
    }

    private String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
