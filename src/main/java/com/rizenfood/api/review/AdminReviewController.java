package com.rizenfood.api.review;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.review.dto.ReviewDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 후기 관리.
 * ★ 효능을 단정하는 후기는 노출 전에 걸러야 한다 (기획서 §9). 승인 없이는 공개되지 않는다.
 */
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminReviewController {

    private final ReviewService service;
    private final AuditService auditService;

    public AdminReviewController(ReviewService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "false") boolean pendingOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ReviewDtos.AdminItem> result = service.listForAdmin(pendingOnly,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    /** 노출 승인 / 숨김 */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Map<String, String>> moderate(
            @PathVariable Long id,
            @RequestBody ReviewDtos.ModerateRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.moderate(id, request.visible(), request.reason());
        auditService.record(admin.id(), admin.displayName(),
                request.visible() ? "APPROVE_REVIEW" : "HIDE_REVIEW",
                "REVIEW", String.valueOf(id), request.reason(), httpRequest);

        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.deleteAsAdmin(id);
        auditService.record(admin.id(), admin.displayName(), "DELETE",
                "REVIEW", String.valueOf(id), null, httpRequest);

        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }
}
