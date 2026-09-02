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
    /** 비회원 장바구니 식별자. 인증 토큰이 아니라 장바구니 소유권만 나타낸다. */
    public static final String CART_GUEST = "rizen_cart";
    private static final String ADMIN_PATH = "/";
    /** 리프레시 토큰은 재발급 엔드포인트에만 실리게 경로를 좁힌다. */
    private static final String REFRESH_PATH = "/api/auth";
    /** 게스트 장바구니 토큰 유효기간(초). 30일. */
    private static final long GUEST_CART_MAX_AGE = 60L * 60 * 24 * 30;

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

    // ── 게스트 장바구니 쿠키 ──────────────────────────────────
    //
    // 인증 토큰이 아니라 "이 브라우저의 장바구니는 이것" 이라는 식별자다.
    // 그래도 HttpOnly 로 둔다 — 자바스크립트가 만질 이유가 없고, 노출을 줄인다.
    // 사이트 전역에서 필요하므로(담기·장바구니·결제) 경로는 "/" 다.

    public ResponseCookie cartGuest(String token) {
        return ResponseCookie.from(CART_GUEST, token)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite(properties.sameSite())
                .path(ADMIN_PATH)
                .maxAge(GUEST_CART_MAX_AGE)
                .build();
    }

    /** 로그인 병합 후 게스트 쿠키를 지운다. */
    public ResponseCookie expiredCartGuest() {
        return ResponseCookie.from(CART_GUEST, "")
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite(properties.sameSite())
                .path(ADMIN_PATH)
                .maxAge(0)
                .build();
    }

    public String readCartGuest(HttpServletRequest request) {
        return read(request, CART_GUEST);
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
