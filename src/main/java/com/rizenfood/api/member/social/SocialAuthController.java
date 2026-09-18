package com.rizenfood.api.member.social;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.member.Member;
import com.rizenfood.api.member.MemberAuthException;
import com.rizenfood.api.member.MemberSessionIssuer;
import com.rizenfood.api.security.AuthCookies;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * 간편 로그인(카카오·네이버).
 *
 *   GET  /api/auth/oauth/providers            켜져 있는 제공자 (화면이 버튼을 띄울지)
 *   GET  /api/auth/oauth/{provider}/start      제공자 로그인 화면으로 보낸다
 *   GET  /api/auth/oauth/{provider}/callback   제공자가 돌려보내는 곳. 결과에 따라 화면으로 보낸다
 *   GET  /api/auth/oauth/pending               동의 화면에 보여줄 정보 (처음 온 사람)
 *   POST /api/auth/oauth/complete              동의 후 가입 마무리
 *
 * start·callback 은 사람이 브라우저로 이동하는 주소라 JSON 대신 화면 주소로 넘긴다(302).
 * 넘기는 주소는 우리 사이트 안 경로뿐이다 — 바깥 주소로 튕기는 통로(오픈 리다이렉트)를 만들지 않는다.
 */
@RestController
@RequestMapping("/api/auth/oauth")
public class SocialAuthController {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthController.class);

    /** 로그인 뒤 기본 도착지 — 메인 화면 (2026-09-18 요청). next 가 있으면 그쪽을 쓴다. */
    static final String DEFAULT_NEXT = "/";
    static final String SIGNUP_PAGE = "/auth/social-signup";
    static final String LOGIN_PAGE = "/auth/login";

    private final SocialLoginService service;
    private final JwtTokenProvider tokenProvider;
    private final AuthCookies cookies;
    private final MemberSessionIssuer sessionIssuer;
    private final SecureRandom random = new SecureRandom();

    public SocialAuthController(SocialLoginService service, JwtTokenProvider tokenProvider,
                                AuthCookies cookies, MemberSessionIssuer sessionIssuer) {
        this.service = service;
        this.tokenProvider = tokenProvider;
        this.cookies = cookies;
        this.sessionIssuer = sessionIssuer;
    }

    @GetMapping("/providers")
    public Map<String, Boolean> providers() {
        return service.enabledProviders();
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable String provider,
                                      @RequestParam(required = false) String next) {
        Optional<SocialProviderClient> client = service.client(provider);
        if (client.isEmpty()) {
            return redirect(loginError("unavailable_provider"));
        }

        String state = randomState();
        String stateToken = tokenProvider.createOAuthState(provider, state, safeNext(next));
        String authorizeUrl = client.get().authorizeUrl(state, service.callbackUrl(provider));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizeUrl))
                .header(HttpHeaders.SET_COOKIE,
                        cookies.oauthState(stateToken, tokenProvider.socialTicketSeconds()).toString())
                .build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest http) {
        List<String> setCookies = new ArrayList<>();
        // 결과와 상관없이 1회용 state 는 지운다 (같은 콜백을 다시 쓰지 못하게).
        setCookies.add(cookies.expiredOauthState().toString());

        // 손님이 제공자 화면에서 취소했다.
        if (error != null) {
            return redirect(loginError("cancelled"), setCookies);
        }

        Optional<JwtTokenProvider.OAuthState> saved =
                tokenProvider.parseOAuthState(cookies.readOauthState(http));
        boolean stateOk = saved.isPresent()
                && provider.equals(saved.get().provider())
                && state != null
                && constantTimeEquals(state, saved.get().state());
        if (!stateOk || code == null || code.isBlank()) {
            // 시간이 지났거나(10분), 다른 사이트가 만든 요청이다.
            return redirect(loginError("expired"), setCookies);
        }

        Optional<SocialProviderClient> client = service.client(provider);
        if (client.isEmpty()) {
            return redirect(loginError("unavailable_provider"), setCookies);
        }

        SocialProfile profile;
        try {
            profile = client.get().fetchProfile(code, state, service.callbackUrl(provider));
        } catch (SocialLoginException e) {
            log.warn("간편 로그인 실패 ({}): {}", provider, e.getMessage());
            return redirect(loginError("failed"), setCookies);
        }

        String next = saved.get().next();
        SocialLoginService.Outcome outcome = service.resolve(profile);

        if (outcome instanceof SocialLoginService.LoggedIn loggedIn) {
            setCookies.addAll(sessionIssuer.sessionCookies(loggedIn.member(), http));
            return redirect(next, setCookies);
        }
        if (outcome instanceof SocialLoginService.NeedsSignup needsSignup) {
            SocialProfile p = needsSignup.profile();
            String ticket = tokenProvider.createSocialSignupTicket(
                    p.provider(), p.providerId(), p.email(), p.emailVerified(), p.name(), next);
            setCookies.add(cookies.socialSignup(ticket, tokenProvider.socialTicketSeconds()).toString());
            return redirect(SIGNUP_PAGE, setCookies);
        }
        if (outcome instanceof SocialLoginService.EmailTaken taken) {
            return redirect(loginError("email_exists") + "&existing="
                    + taken.existingProvider().toLowerCase(), setCookies);
        }
        return redirect(loginError("unavailable"), setCookies);
    }

    /** 동의 화면에 보여줄 정보. 이메일은 제공자가 확인해 준 것만 보여준다. */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> pending(HttpServletRequest http) {
        Optional<JwtTokenProvider.SocialSignupTicket> ticket =
                tokenProvider.parseSocialSignupTicket(cookies.readSocialSignup(http));
        if (ticket.isEmpty()) {
            return expiredTicket();
        }
        JwtTokenProvider.SocialSignupTicket t = ticket.get();
        Map<String, Object> body = new HashMap<>();
        body.put("provider", t.provider());
        body.put("name", t.name());
        body.put("email", t.emailVerified() ? t.email() : null);
        return ResponseEntity.ok(body);
    }

    public record CompleteRequest(
            @AssertTrue(message = "이용약관과 개인정보처리방침에 동의해 주세요.") boolean agreeRequired,
            @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.") boolean ageOver14,
            boolean agreeMarketing) {
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> complete(@Valid @RequestBody CompleteRequest request,
                                                        HttpServletRequest http) {
        Optional<JwtTokenProvider.SocialSignupTicket> ticket =
                tokenProvider.parseSocialSignupTicket(cookies.readSocialSignup(http));
        if (ticket.isEmpty()) {
            return expiredTicket();
        }
        JwtTokenProvider.SocialSignupTicket t = ticket.get();
        SocialProfile profile = new SocialProfile(
                t.provider(), t.providerId(), t.email(), t.emailVerified(), t.name());

        Member member = service.completeSignup(profile, request.ageOver14(), request.agreeMarketing());

        List<String> setCookies = new ArrayList<>(sessionIssuer.sessionCookies(member, http));
        setCookies.add(cookies.expiredSocialSignup().toString());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, setCookies.toArray(String[]::new))
                .body(Map.of("next", safeNext(t.next())));
    }

    /** 가입 마무리 실패(같은 이메일 계정이 생김 등). 사유를 화면에 그대로 보여줄 수 있는 문장이다. */
    @ExceptionHandler(MemberAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(MemberAuthException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "SOCIAL_SIGNUP_FAILED", "message", e.getMessage()));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────

    /**
     * 로그인 뒤 돌아갈 화면. 우리 사이트 안 경로만 허용한다.
     * "//evil.com", "/\evil.com" 같은 값은 브라우저가 바깥 주소로 해석하므로 막는다.
     */
    static String safeNext(String next) {
        if (next == null || next.isBlank()) {
            return DEFAULT_NEXT;
        }
        String v = next.trim();
        boolean internal = v.startsWith("/")
                && !v.startsWith("//")
                && !v.contains("\\")
                && !v.contains("://")
                && !v.startsWith("/api/")
                && v.chars().noneMatch(Character::isISOControl);
        return internal ? v : DEFAULT_NEXT;
    }

    private static String loginError(String code) {
        return LOGIN_PAGE + "?social_error=" + code;
    }

    private ResponseEntity<Void> redirect(String path) {
        return redirect(path, List.of());
    }

    private ResponseEntity<Void> redirect(String path, List<String> setCookies) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(path))
                .header(HttpHeaders.SET_COOKIE, setCookies.toArray(String[]::new))
                .build();
    }

    private ResponseEntity<Map<String, Object>> expiredTicket() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "SOCIAL_SIGNUP_EXPIRED",
                "message", "가입 시간이 지났습니다. 간편 로그인을 다시 눌러 주세요."));
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
