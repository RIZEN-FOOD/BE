package com.rizenfood.api.product;

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
import com.rizenfood.api.product.dto.ProductDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 상품 API.
 *
 * 클래스 단위로 @PreAuthorize 를 걸어, 메서드를 새로 추가해도 권한 검사가 빠지지 않게 한다.
 * 하나만 빠져도 구멍이다 (기획서 §10).
 */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminProductController {

    private final ProductService service;
    private final AuditService auditService;

    public AdminProductController(ProductService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<ProductDtos.AdminListItem> result = service.listForAdmin(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("id"))));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @GetMapping("/{id}")
    public ProductDtos.Detail detail(@PathVariable Long id) {
        return service.getForAdmin(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody ProductDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        Long id = service.create(request);
        auditService.record(admin.id(), admin.displayName(), "CREATE",
                "PRODUCT", String.valueOf(id), request.nameKo(), httpRequest);

        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.update(id, request);
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "PRODUCT", String.valueOf(id), request.nameKo(), httpRequest);

        return ResponseEntity.ok(Map.of("message", "저장되었습니다."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.delete(id);
        auditService.record(admin.id(), admin.displayName(), "DELETE",
                "PRODUCT", String.valueOf(id), null, httpRequest);

        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    /** 드래그 정렬 결과를 받는다. */
    @PatchMapping("/order")
    public ResponseEntity<Map<String, String>> reorder(
            @Valid @RequestBody ProductDtos.ReorderRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.reorder(request.orderedIds());
        auditService.record(admin.id(), admin.displayName(), "REORDER",
                "PRODUCT", null, "상품 " + request.orderedIds().size() + "건", httpRequest);

        return ResponseEntity.ok(Map.of("message", "순서가 저장되었습니다."));
    }

    /** 노출 · 메인노출 토글 */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Map<String, String>> visibility(
            @PathVariable Long id,
            @RequestBody ProductDtos.VisibilityRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.updateVisibility(id, request.visible(), request.featured());
        auditService.record(admin.id(), admin.displayName(), "TOGGLE_VISIBILITY",
                "PRODUCT", String.valueOf(id),
                "노출=" + request.visible() + " 메인=" + request.featured(), httpRequest);

        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }
}
