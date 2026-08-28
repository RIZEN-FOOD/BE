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

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AuthCookies cookies) {
        this.tokenProvider = tokenProvider;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // 관리자 토큰을 먼저 본다. 있으면 관리자로 인증한다.
            var admin = tokenProvider.parseAdminToken(cookies.readAdminToken(request));
            if (admin.isPresent()) {
                var authority = new SimpleGrantedAuthority("ROLE_" + admin.get().role());
                var authentication = new UsernamePasswordAuthenticationToken(
                        admin.get(), null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // 관리자 토큰이 없으면 회원 토큰을 본다.
                tokenProvider.parseMemberToken(cookies.readMemberAccess(request)).ifPresent(member -> {
                    var authority = new SimpleGrantedAuthority("ROLE_MEMBER");
                    var authentication = new UsernamePasswordAuthenticationToken(
                            member, null, List.of(authority));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        chain.doFilter(request, response);
    }
}
