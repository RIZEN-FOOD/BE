package com.rizenfood.api.coupon;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 관리자 할인코드 API. 클래스 단위 @PreAuthorize 로 권한 검사 누락을 막는다. */
@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminCouponController {

    private final CouponAdminService service;

    public AdminCouponController(CouponAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<CouponDtos.AdminItem> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CouponDtos.AdminItem detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<CouponDtos.AdminItem> create(@Valid @RequestBody CouponDtos.SaveRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public CouponDtos.AdminItem update(@PathVariable Long id,
                                       @Valid @RequestBody CouponDtos.SaveRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "지웠습니다."));
    }
}
