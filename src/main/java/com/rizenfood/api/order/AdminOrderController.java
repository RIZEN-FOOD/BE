package com.rizenfood.api.order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.order.dto.AdminOrderDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 주문 관리.
 * 목록·상세 조회, 상태 변경, 운송장 등록, 출고용 엑셀. 모든 작업은 감사 로그에 남긴다.
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminOrderController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OrderService orderService;
    private final AuditService auditService;

    public AdminOrderController(OrderService orderService, AuditService auditService) {
        this.orderService = orderService;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AdminOrderDtos.Summary> result = orderService.adminList(status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    /**
     * 출고 대행사에 넘길 주문 엑셀(.xlsx).
     * 받는 분 연락처·주소가 복호화돼 담기므로 내려받을 때마다 감사 로그를 남긴다.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportForShipping(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        OrderService.ShippingExport export = orderService.adminExportForShipping(status);
        String scope = (status == null || status.isBlank()) ? "출고 대기" : status;
        auditService.record(admin.id(), admin.displayName(), "EXPORT",
                "ORDER", null, "출고용 엑셀 " + scope + " " + export.orderCount() + "건", httpRequest);

        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("라이즌푸드_출고_" + today + ".xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(export.file());
    }

    @GetMapping("/{orderNo}")
    public AdminOrderDtos.Detail get(@PathVariable String orderNo) {
        return orderService.adminGet(orderNo);
    }

    @PatchMapping("/{orderNo}/status")
    public ResponseEntity<Map<String, String>> changeStatus(
            @PathVariable String orderNo,
            @Valid @RequestBody AdminOrderDtos.StatusRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        orderService.adminChangeStatus(orderNo, req.status());
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "ORDER", null, orderNo + " → " + req.status(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "주문 상태를 변경했습니다."));
    }

    @PutMapping("/{orderNo}/delivery")
    public ResponseEntity<Map<String, String>> ship(
            @PathVariable String orderNo,
            @Valid @RequestBody AdminOrderDtos.ShipRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        orderService.adminShip(orderNo, req.carrier(), req.trackingNo());
        auditService.record(admin.id(), admin.displayName(), "UPDATE",
                "DELIVERY", null, orderNo + " " + req.carrier() + " " + req.trackingNo(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "운송장을 등록했습니다."));
    }

    /** 송장 엑셀 한 건의 크기 상한. 주문 수천 건짜리도 1MB 를 넘지 않는다. */
    private static final long MAX_SHEET_BYTES = 8L * 1024 * 1024;

    /**
     * 출고 대행사가 송장을 채워 보낸 엑셀을 한 번에 반영한다.
     *
     * 잘못된 줄이 섞여 있어도 나머지는 반영하고, 못 넣은 줄만 이유와 함께 돌려준다.
     * 같은 파일을 두 번 올려도 이미 들어간 송장은 건너뛴다.
     */
    @PostMapping("/delivery/bulk")
    public ResponseEntity<Map<String, Object>> shipBulk(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("엑셀 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_SHEET_BYTES) {
            throw new IllegalArgumentException("엑셀 파일이 너무 큽니다. 8MB 이하로 올려 주세요.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("엑셀 파일(.xlsx)만 올릴 수 있습니다.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽지 못했습니다. 다시 올려 주세요.");
        }

        OrderService.BulkShipResult result = orderService.adminBulkShip(bytes);
        auditService.record(admin.id(), admin.displayName(), "UPDATE", "DELIVERY", null,
                "송장 엑셀 등록 " + result.applied() + "건 반영 · " + result.failures().size() + "건 실패",
                httpRequest);

        String message = result.applied() + "건의 운송장을 등록했습니다.";
        if (!result.failures().isEmpty()) {
            message += " " + result.failures().size() + "건은 처리하지 못했습니다.";
        }
        return ResponseEntity.ok(Map.of(
                "message", message,
                "total", result.total(),
                "applied", result.applied(),
                "skipped", result.skipped(),
                "failures", result.failures()));
    }
}
