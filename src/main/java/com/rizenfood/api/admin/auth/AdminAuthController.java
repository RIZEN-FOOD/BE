package com.rizenfood.api.admin.auth;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.admin.AdminUser;
import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.security.AuthCookies;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 로그인 · 로그아웃 · 내 정보.
 *
 * 토큰은 응답 본문이 아니라 HttpOnly 쿠키로 나간다.
 * 본문에 담으면 프론트가 어딘가에 저장하게 되고, 그 순간 XSS 에 노출된다.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final JwtTokenProvider tokenProvider;
    private final AuthCookies cookies;
    private final AuditService auditService;

    public AdminAuthController(AdminAuthService authService, JwtTokenProvider tokenProvider,
                               AuthCookies cookies, AuditService auditService) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
        this.cookies = cookies;
        this.auditService = auditService;
    }

    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") String username,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        AdminUser admin = authService.authenticate(request.username(), request.password());

        String token = tokenProvider.createAdminToken(
                admin.getId(), admin.getUsername(), admin.getDisplayName(), admin.getRole());
        ResponseCookie cookie = cookies.adminToken(token, tokenProvider.adminExpirySeconds());

        auditService.record(admin.getId(), admin.getDisplayName(), "LOGIN",
                "ADMIN_USER", String.valueOf(admin.getId()), null, httpRequest);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of(
                        "displayName", admin.getDisplayName(),
                        "role", admin.getRole()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.expiredAdminToken().toString())
                .body(Map.of("message", "로그아웃되었습니다."));
    }

    /** 새로고침 시 로그인 상태를 확인하는 용도 */
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin) {
        return ResponseEntity.ok(Map.of(
                "displayName", admin.displayName(),
                "role", admin.role()));
    }
}
