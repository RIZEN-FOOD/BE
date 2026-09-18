package com.rizenfood.api.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 쿠키의 JWT 를 확인해 인증 정보를 심는다.
 *
 * 토큰이 없거나 틀려도 여기서 막지 않는다. 인증을 비워둔 채 넘기고,
 * 실제 차단은 SecurityConfig 의 경로 규칙과 각 API 의 @PreAuthorize 가 한다.
 * 그래야 공개 API 는 토큰 없이도 그대로 동작한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final AuthCookies cookies;
    private final com.rizenfood.api.admin.AdminSessionGuard adminSessions;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AuthCookies cookies,
                                   com.rizenfood.api.admin.AdminSessionGuard adminSessions) {
        this.tokenProvider = tokenProvider;
        this.cookies = cookies;
        this.adminSessions = adminSessions;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // ★ 한 브라우저에 관리자 쿠키와 회원 쿠키가 같이 있을 수 있다 (대표가 관리자에 로그인한 채로
            //   쇼핑몰을 둘러보는 경우). 그때 요청이 어느 쪽 것인지는 주소로 가른다.
            //   관리 API 는 관리자 자격으로, 그 밖의 요청은 회원 자격으로 먼저 본다.
            //   (2026-09-18: 관리자 쿠키가 있으면 회원 요청이 403 이 되어 마이페이지가 로그인 화면으로
            //    되돌아가던 문제를 고친다.)
            boolean adminArea = request.getRequestURI().startsWith("/api/admin");
            boolean authenticated = adminArea
                    ? authenticateAdmin(request) || authenticateMember(request)
                    : authenticateMember(request) || authenticateAdmin(request);
            if (!authenticated) {
                // 인증 없이 통과시킨다. 공개 API 는 그대로 동작하고, 보호된 API 는 401 이 된다.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 관리자 쿠키로 인증한다.
     * 서명이 맞아도 비밀번호 변경·계정 중지·로그아웃으로 끊긴 토큰이면 인증하지 않는다.
     */
    private boolean authenticateAdmin(HttpServletRequest request) {
        var admin = tokenProvider.parseAdminToken(cookies.readAdminToken(request))
                .filter(adminSessions::isCurrent);
        if (admin.isEmpty()) {
            return false;
        }
        var authority = new SimpleGrantedAuthority("ROLE_" + admin.get().role());
        var authentication = new UsernamePasswordAuthenticationToken(admin.get(), null, List.of(authority));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    /** 회원 쿠키로 인증한다. */
    private boolean authenticateMember(HttpServletRequest request) {
        var member = tokenProvider.parseMemberToken(cookies.readMemberAccess(request));
        if (member.isEmpty()) {
            return false;
        }
        var authority = new SimpleGrantedAuthority("ROLE_MEMBER");
        var authentication = new UsernamePasswordAuthenticationToken(member.get(), null, List.of(authority));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }
}
