package com.rizenfood.api.popup;

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
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** 관리자 팝업 API. 클래스 단위 @PreAuthorize 로 권한 검사 누락을 막는다. */
@RestController
@RequestMapping("/api/admin/popups")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminPopupController {

    private final PopupService service;
    private final AuditService auditService;

    public AdminPopupController(PopupService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<PopupDtos.AdminItem> list() {
        return service.listForAdmin();
    }

    @GetMapping("/{id}")
    public PopupDtos.AdminItem detail(@PathVariable Long id) {
        return service.getForAdmin(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody PopupDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        Long id = service.create(request);
        auditService.record(admin.id(), admin.displayName(), "CREATE",
                "POPUP", String.valueOf(id), request.title(), httpRequest);
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long id,
            @Valid @RequestBody PopupDtos.SaveRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.update(id, request);
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "POPUP", String.valueOf(id), request.title(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "저장되었습니다."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.delete(id);
        auditService.record(admin.id(), admin.displayName(), "DELETE",
                "POPUP", String.valueOf(id), null, httpRequest);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Map<String, String>> visibility(
            @PathVariable Long id,
            @RequestBody PopupDtos.VisibilityRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.updateVisibility(id, request.visible());
        auditService.record(admin.id(), admin.displayName(), "TOGGLE_VISIBILITY",
                "POPUP", String.valueOf(id), "노출=" + request.visible(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }

    @PutMapping("/order")
    public ResponseEntity<Map<String, String>> reorder(
            @RequestBody PopupDtos.OrderRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {
        service.reorder(request.ids());
        auditService.record(admin.id(), admin.displayName(), "REORDER",
                "POPUP", null, String.valueOf(request.ids()), httpRequest);
        return ResponseEntity.ok(Map.of("message", "순서가 저장되었습니다."));
    }
}
