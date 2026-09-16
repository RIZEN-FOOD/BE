package com.rizenfood.api.member;

import java.util.List;

import org.springframework.stereotype.Component;

import com.rizenfood.api.security.AuthCookies;
import com.rizenfood.api.security.ClientIpResolver;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 회원 로그인 상태를 만든다 — access·refresh 쿠키 두 장.
 *
 * 이메일 로그인·가입과 간편 로그인이 같은 방식으로 세션을 만들도록 한 곳에 둔다.
 * (쿠키 속성이 한쪽만 바뀌어 로그인 방식마다 보안 수준이 달라지는 일을 막는다.)
 */
@Component
public class MemberSessionIssuer {

    private final MemberAuthService authService;
    private final JwtTokenProvider tokenProvider;
    private final AuthCookies cookies;
    private final ClientIpResolver ipResolver;

    public MemberSessionIssuer(MemberAuthService authService, JwtTokenProvider tokenProvider,
                               AuthCookies cookies, ClientIpResolver ipResolver) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
        this.cookies = cookies;
        this.ipResolver = ipResolver;
    }

    /** Set-Cookie 헤더에 넣을 값 두 개 (access, refresh). */
    public List<String> sessionCookies(Member member, HttpServletRequest http) {
        String access = tokenProvider.createMemberAccessToken(member.getId(), member.getName());
        String refresh = authService.issueRefreshToken(
                member.getId(), http.getHeader("User-Agent"), ipResolver.resolve(http));
        return List.of(
                cookies.memberAccess(access, tokenProvider.memberAccessSeconds()).toString(),
                cookies.memberRefresh(refresh, tokenProvider.memberRefreshSeconds()).toString());
    }
}
