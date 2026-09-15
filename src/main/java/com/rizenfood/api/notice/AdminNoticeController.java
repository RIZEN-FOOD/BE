package com.rizenfood.api.notice;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.notice.dto.NoticeDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 공지 API.
 * 관리자 목록은 임시저장·예약·숨김까지 전부 본다.
 */
@RestController
@RequestMapping("/api/admin/notices")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminNoticeController {

    private final NoticeService service;
    private final AuditService auditService;

    public AdminNoticeController(NoticeService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<NoticeDtos.AdminItem> result = service.listForAdmin(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("id"))));
        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @GetMapping("/{id}")
    public NoticeDtos.AdminItem detail(@PathVariable Long id) {
        return service.getForAdmin(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody NoticeDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        Long id = service.create(request);
        auditService.record(admin.id(), admin.displayName(), "CREATE",
                "NOTICE", String.valueOf(id), request.title(), httpRequest);
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long id,
            @Valid @RequestBody NoticeDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.update(id, request);
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "NOTICE", String.valueOf(id), request.title(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "저장되었습니다."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.delete(id);
        auditService.record(admin.id(), admin.displayName(), "DELETE",
                "NOTICE", String.valueOf(id), null, httpRequest);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Map<String, String>> visibility(
            @PathVariable Long id,
            @RequestBody NoticeDtos.VisibilityRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.updateVisibility(id, request.visible());
        auditService.record(admin.id(), admin.displayName(), "TOGGLE_VISIBILITY",
                "NOTICE", String.valueOf(id), "노출=" + request.visible(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }
}
