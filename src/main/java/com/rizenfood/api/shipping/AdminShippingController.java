package com.rizenfood.api.shipping;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 배송비 정책.
 *
 * ★ 배송비·무료배송 임계액은 코드에 박지 않고 이 정책에서 읽는다 (CLAUDE.md 규칙 5).
 *   대표가 여기서 바꾸면 결제·배송안내 전부에 즉시 반영된다.
 *
 * 활성 정책은 하나뿐이라(부분 유니크 인덱스), 그 하나를 조회·수정한다.
 * 금액 항목만 수정하고 활성 여부는 바꾸지 않는다(정책이 사라지면 배송비가 0으로 샌다).
 */
@RestController
@RequestMapping("/api/admin/shipping-policy")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminShippingController {

    private final ShippingPolicyRepository repository;
    private final AuditService auditService;

    public AdminShippingController(ShippingPolicyRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public record PolicyView(Long id, String name, int baseFee, Integer freeThreshold, int islandExtraFee) {
    }

    public record UpdateRequest(String name, Integer baseFee, Integer freeThreshold, Integer islandExtraFee) {
    }

    @GetMapping
    public PolicyView current() {
        ShippingPolicy p = activePolicy();
        return new PolicyView(p.getId(), p.getName(), p.getBaseFee(), p.getFreeThreshold(), p.getIslandExtraFee());
    }

    @PutMapping
    @Transactional
    public ResponseEntity<Map<String, String>> update(
            @RequestBody UpdateRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest httpRequest) {

        ShippingPolicy p = activePolicy();

        int baseFee = req.baseFee() == null ? 0 : req.baseFee();
        int islandExtraFee = req.islandExtraFee() == null ? 0 : req.islandExtraFee();
        Integer freeThreshold = req.freeThreshold();

        if (baseFee < 0 || islandExtraFee < 0 || (freeThreshold != null && freeThreshold < 0)) {
            throw new IllegalArgumentException("금액은 0원 이상이어야 합니다.");
        }
        String name = (req.name() == null || req.name().isBlank()) ? p.getName() : req.name().trim();

        p.update(name, baseFee, freeThreshold, islandExtraFee);

        auditService.record(admin.id(), admin.displayName(), "UPDATE_SHIPPING_POLICY",
                "SHIPPING_POLICY", String.valueOf(p.getId()),
                "base=" + baseFee + " free=" + freeThreshold + " island=" + islandExtraFee, httpRequest);

        return ResponseEntity.ok(Map.of("message", "저장되었습니다."));
    }

    private ShippingPolicy activePolicy() {
        return repository.findFirstByVisibleTrueOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("활성 배송비 정책이 없습니다."));
    }
}
