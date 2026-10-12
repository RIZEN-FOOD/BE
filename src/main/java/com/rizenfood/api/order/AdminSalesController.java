package com.rizenfood.api.order;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.order.dto.SalesDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 [매출] — 기간별 과세·면세 매출과 엑셀 (2026-10-12).
 * 날짜는 yyyy-MM-dd, 시작·끝 날짜 모두 포함(한국 시간).
 */
@RestController
@RequestMapping("/api/admin/sales")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminSalesController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SalesService salesService;
    private final AuditService auditService;

    public AdminSalesController(SalesService salesService, AuditService auditService) {
        this.salesService = salesService;
        this.auditService = auditService;
    }

    @GetMapping
    public SalesDtos.Report get(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return salesService.report(from, to);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        byte[] file = salesService.export(from, to);
        auditService.record(admin.id(), admin.displayName(), "EXPORT",
                "SALES", null, "매출 엑셀 " + from + " ~ " + to, httpRequest);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("라이즌푸드_매출_" + from + "_" + to + ".xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(file);
    }
}
