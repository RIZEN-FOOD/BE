package com.rizenfood.api.banner;

import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.banner.dto.HeroImageSlideDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 메인 히어로 — 이미지 한 장 슬라이드 (V41, 2026-10-13).
 * 누끼 슬라이드는 /api/admin/hero-banners(상품 히어로 필드)에서 다룬다.
 */
@RestController
@RequestMapping("/api/admin/hero-image-slides")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminHeroImageSlideController {

    private final HeroImageSlideService service;
    private final AuditService auditService;

    public AdminHeroImageSlideController(HeroImageSlideService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<HeroImageSlideDtos.AdminItem> list() {
        return service.listForAdmin();
    }

    @GetMapping("/{id}")
    public HeroImageSlideDtos.AdminItem get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody HeroImageSlideDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        Long id = service.create(request);
        auditService.record(admin.id(), admin.displayName(), "CREATE",
                "HERO_IMAGE_SLIDE", String.valueOf(id), request.altText(), httpRequest);
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long id,
            @Valid @RequestBody HeroImageSlideDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.update(id, request);
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "HERO_IMAGE_SLIDE", String.valueOf(id), request.altText(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "저장되었습니다."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.delete(id);
        auditService.record(admin.id(), admin.displayName(), "DELETE",
                "HERO_IMAGE_SLIDE", String.valueOf(id), null, httpRequest);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Map<String, String>> enabled(
            @PathVariable Long id,
            @RequestBody HeroImageSlideDtos.EnabledRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.setEnabled(id, request.enabled());
        auditService.record(admin.id(), admin.displayName(), "TOGGLE_VISIBILITY",
                "HERO_IMAGE_SLIDE", String.valueOf(id), "노출=" + request.enabled(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }
}
