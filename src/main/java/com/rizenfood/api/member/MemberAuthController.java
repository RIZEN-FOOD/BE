package com.rizenfood.api.member;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.member.dto.MemberDtos;
import com.rizenfood.api.security.AuthCookies;
import com.rizenfood.api.security.ClientIpResolver;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 회원 인증 API.
 *
 * 토큰은 응답 본문이 아니라 HttpOnly 쿠키로 나간다.
 *  - access  : 짧은 수명, 모든 요청에 실린다
 *  - refresh : 긴 수명, /api/auth 경로에만 실린다 (재발급 전용)
 */
@RestController
@RequestMapping("/api/auth")
public class MemberAuthController {

    private final MemberAuthService service;
    private final JwtTokenProvider tokenProvider;
    private final AuthCookies cookies;
    private final ClientIpResolver ipResolver;
    private final MemberSessionIssuer sessionIssuer;

    public MemberAuthController(MemberAuthService service, JwtTokenProvider tokenProvider,
                                AuthCookies cookies, ClientIpResolver ipResolver,
                                MemberSessionIssuer sessionIssuer) {
        this.service = service;
        this.tokenProvider = tokenProvider;
        this.cookies = cookies;
        this.ipResolver = ipResolver;
        this.sessionIssuer = sessionIssuer;
    }

    /** 이메일 사용 가능 여부 (회원가입 중복확인) */
    @PostMapping("/check-email")
    public Map<String, Boolean> checkEmail(@Valid @RequestBody MemberDtos.CheckEmailRequest request) {
        return Map.of("available", service.isEmailAvailable(request.email()));
    }

    @PostMapping("/signup")
    public ResponseEntity<MemberDtos.MemberResponse> signup(
            @Valid @RequestBody MemberDtos.SignupRequest request,
            HttpServletRequest http) {

        Member member = service.signup(
                request.email(), request.password(), request.name(),
                request.phone(), request.agreeMarketing(), request.ageOver14());

        // 가입 후 바로 로그인 상태로 만든다.
        return issueSession(member, http, 201);
    }

    @PostMapping("/login")
    public ResponseEntity<MemberDtos.MemberResponse> login(
            @Valid @RequestBody MemberDtos.LoginRequest request,
            HttpServletRequest http) {

        Member member = service.login(request.email(), request.password());
        return issueSession(member, http, 200);
    }

    /** access 토큰이 만료됐을 때 refresh 로 재발급 */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest http) {
        var result = service.rotate(
                cookies.readMemberRefresh(http), http.getHeader("User-Agent"), clientIp(http));

        Member member = service.get(result.memberId());
        String access = tokenProvider.createMemberAccessToken(member.getId(), member.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.memberAccess(access, tokenProvider.memberAccessSeconds()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.memberRefresh(result.newRefreshRaw(), tokenProvider.memberRefreshSeconds()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.memberHint(tokenProvider.memberRefreshSeconds()).toString())
                .body(Map.of("message", "갱신되었습니다."));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        service.revokeAll(me.id());
        return clearSession().body(Map.of("message", "로그아웃되었습니다."));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public MemberDtos.MemberResponse me(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        Member member = service.get(me.id());
        // 자리 표시 주소(간편 로그인에서 이메일을 못 받은 경우)는 화면에 내보내지 않는다.
        return new MemberDtos.MemberResponse(
                member.getId(), member.contactEmail(), member.getName(), member.getProvider());
    }

    /** 회원 탈퇴 */
    @DeleteMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Map<String, String>> withdraw(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        service.withdraw(me.id());
        return clearSession().body(Map.of("message", "탈퇴가 완료되었습니다."));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────

    private ResponseEntity<MemberDtos.MemberResponse> issueSession(
            Member member, HttpServletRequest http, int status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        sessionIssuer.sessionCookies(member, http).toArray(String[]::new))
                .body(new MemberDtos.MemberResponse(
                        member.getId(), member.contactEmail(), member.getName(), member.getProvider()));
    }

    private ResponseEntity.BodyBuilder clearSession() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.expiredMemberAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.expiredMemberRefresh().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.expiredMemberHint().toString());
    }

    /** 로그인 기기 기록용 접속자 IP. 위조 가능한 X-Forwarded-For 대신 ClientIpResolver 가 정한다. */
    private String clientIp(HttpServletRequest request) {
        return ipResolver.resolve(request);
    }
}
