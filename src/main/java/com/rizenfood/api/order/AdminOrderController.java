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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.order.dto.AdminOrderDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 관리자 주문 관리.
 * 목록·상세 조회, 상태 변경, 운송장 등록. 모든 작업은 감사 로그에 남긴다.
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminOrderController {

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
}
