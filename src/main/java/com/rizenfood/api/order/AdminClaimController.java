package com.rizenfood.api.order;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.order.dto.ClaimDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 취소·반품·교환 처리.
 */
@RestController
@RequestMapping("/api/admin/claims")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminClaimController {

    private final ClaimService claimService;
    private final AuditService auditService;

    public AdminClaimController(ClaimService claimService, AuditService auditService) {
        this.claimService = claimService;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ClaimDtos.AdminItem> result = claimService.adminList(status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClaimDtos.View> process(
            @PathVariable Long id,
            @Valid @RequestBody ClaimDtos.ProcessRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        ClaimDtos.View view = claimService.process(id, req);
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "ORDER_CLAIM", String.valueOf(id), req.status(), httpRequest);
        return ResponseEntity.ok(view);
    }
}
