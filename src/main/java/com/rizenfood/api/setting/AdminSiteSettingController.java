package com.rizenfood.api.setting;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.security.JwtTokenProvider;
import com.rizenfood.api.setting.dto.SiteSettingDtos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 사이트 설정.
 * 사업자정보·SNS 링크·메인 섹션 노출처럼 코드에 박지 않아야 할 값들을 여기서 바꾼다.
 */
@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminSiteSettingController {

    private final SiteSettingService service;
    private final AuditService auditService;

    public AdminSiteSettingController(SiteSettingService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<SiteSettingDtos.AdminItem> list() {
        return service.listForAdmin();
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> update(
            @Valid @RequestBody SiteSettingDtos.UpdateRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.updateValues(request.values());
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "SITE_SETTING", null, request.values().keySet().toString(), httpRequest);

        return ResponseEntity.ok(Map.of("message", "저장되었습니다."));
    }
}
