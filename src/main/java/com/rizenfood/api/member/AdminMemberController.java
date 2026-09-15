package com.rizenfood.api.member;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.member.dto.AdminMemberDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 회원 관리.
 *
 * ★ 개인정보를 다루므로 (CLAUDE.md 규칙 6):
 *   - 목록·상세는 휴대폰을 마스킹해서만 준다.
 *   - 전체 번호 조회(revealPhone)와 잠금 해제·상태 변경은 전부 감사로그를 남긴다.
 *   - 모든 엔드포인트는 관리자 권한이 있어야 한다 (@PreAuthorize).
 *
 * 조치는 잠금 해제 / 정지·해제 두 가지뿐이다. 회원 정보 수정·삭제는 제공하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminMemberController {

    private final AdminMemberService service;
    private final AuditService auditService;

    public AdminMemberController(AdminMemberService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AdminMemberDtos.ListItem> result = service.list(q, status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @GetMapping("/{id}")
    public AdminMemberDtos.Detail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    /** 전체 휴대폰 번호 조회 — 열람 자체를 감사로그에 남긴다. */
    @GetMapping("/{id}/phone")
    public AdminMemberDtos.PhoneResponse phone(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        String phone = service.revealPhone(id);
        auditService.record(admin.id(), admin.displayName(), "VIEW_MEMBER_PHONE",
                "MEMBER", String.valueOf(id), null, httpRequest);
        return new AdminMemberDtos.PhoneResponse(phone);
    }

    /** 로그인 실패 누적 잠금 해제. */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, String>> unlock(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.unlock(id);
        auditService.record(admin.id(), admin.displayName(), "UNLOCK_MEMBER",
                "MEMBER", String.valueOf(id), null, httpRequest);
        return ResponseEntity.ok(Map.of("message", "잠금을 해제했습니다."));
    }

    /** 상태 변경 (ACTIVE ↔ SUSPENDED). */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> changeStatus(
            @PathVariable Long id,
            @RequestBody AdminMemberDtos.StatusRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.changeStatus(id, request.status());
        auditService.record(admin.id(), admin.displayName(),
                "SUSPENDED".equals(request.status()) ? "SUSPEND_MEMBER" : "REACTIVATE_MEMBER",
                "MEMBER", String.valueOf(id), request.reason(), httpRequest);
        return ResponseEntity.ok(Map.of("message",
                "SUSPENDED".equals(request.status()) ? "계정을 정지했습니다." : "정지를 해제했습니다."));
    }
}
