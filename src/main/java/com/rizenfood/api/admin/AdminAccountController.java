package com.rizenfood.api.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 관리 (최고관리자 전용).
 *
 * 계정 추가·수정·비밀번호 초기화. 모두 감사 로그에 남긴다. 비밀번호 값은 로그에 쓰지 않는다.
 * 삭제는 두지 않는다 — 감사 기록이 가리키는 계정이 사라지지 않게, 사용 중지로 대신한다.
 */
@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAccountController {

    private final AdminAccountService service;
    private final AuditService auditService;

    public AdminAccountController(AdminAccountService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    public record CreateRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") String username,
            @NotBlank(message = "이름을 입력해 주세요.") String displayName,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
            @NotBlank(message = "권한을 선택해 주세요.") String role) {
    }

    public record UpdateRequest(
            @NotBlank(message = "이름을 입력해 주세요.") String displayName,
            @NotBlank(message = "권한을 선택해 주세요.") String role,
            boolean enabled) {
    }

    public record PasswordRequest(@NotBlank(message = "새 비밀번호를 입력해 주세요.") String newPassword) {
    }

    @GetMapping
    public List<AdminAccountService.View> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AdminAccountService.View> create(
            @Valid @RequestBody CreateRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest http) {
        AdminAccountService.View created = service.create(req.username(), req.displayName(), req.password(), req.role());
        auditService.record(admin.id(), admin.displayName(), "CREATE", "ADMIN_USER",
                String.valueOf(created.id()), created.username() + " (" + created.role() + ")", http);
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/{id}")
    public AdminAccountService.View update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest http) {
        AdminAccountService.View updated = service.update(admin.id(), id, req.displayName(), req.role(), req.enabled());
        auditService.record(admin.id(), admin.displayName(), "UPDATE", "ADMIN_USER", String.valueOf(id),
                "권한=" + updated.role() + ", 사용=" + updated.enabled(), http);
        return updated;
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest http) {
        service.resetPassword(admin.id(), id, req.newPassword());
        auditService.record(admin.id(), admin.displayName(), "PASSWORD_RESET", "ADMIN_USER",
                String.valueOf(id), null, http);
        return ResponseEntity.ok(Map.of("message", "비밀번호를 바꿨습니다. 그 계정의 기존 로그인은 모두 끊겼습니다."));
    }
}
