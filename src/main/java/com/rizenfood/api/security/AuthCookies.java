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
    private static final String ADMIN_PATH = "/";

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
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ADMIN_TOKEN.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
