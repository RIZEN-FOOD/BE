package com.rizenfood.api.inquiry;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.inquiry.dto.InquiryDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** 관리자 문의함. 기획서 §7.4 — 목록·상태 관리·답변 작성. */
@RestController
@RequestMapping("/api/admin/inquiries")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminInquiryController {

    private final InquiryService service;
    private final AuditService auditService;

    public AdminInquiryController(InquiryService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<InquiryDtos.AdminItem> result = service.listForAdmin(status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @PatchMapping("/{id}/answer")
    public ResponseEntity<Map<String, String>> answer(
            @PathVariable Long id,
            @Valid @RequestBody InquiryDtos.AnswerRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.answer(id, request.answer());
        auditService.record(admin.id(), admin.displayName(), "ANSWER_INQUIRY",
                "INQUIRY", String.valueOf(id), null, httpRequest);

        return ResponseEntity.ok(Map.of("message", "답변이 등록되었습니다."));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<Map<String, String>> close(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        service.close(id);
        auditService.record(admin.id(), admin.displayName(), "CLOSE_INQUIRY",
                "INQUIRY", String.valueOf(id), null, httpRequest);

        return ResponseEntity.ok(Map.of("message", "종료되었습니다."));
    }
}
