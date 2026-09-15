package com.rizenfood.api.shipping;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 배송비 정책 조회. 인증 불필요.
 *
 * ★ 배송·교환·환불 안내 페이지가 배송비·무료배송 임계액을 이 값으로 표시한다.
 *   숫자를 코드나 화면에 박지 않고 shipping_policy 한 곳에서만 읽게 하기 위한 창구다
 *   (CLAUDE.md 규칙 5).
 */
@RestController
@RequestMapping("/api/shipping-policy")
public class ShippingController {

    private final ShippingPolicyRepository repository;

    public ShippingController(ShippingPolicyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> current() {
        Map<String, Object> body = new HashMap<>();
        repository.findFirstByVisibleTrueOrderByIdAsc().ifPresent(p -> {
            body.put("baseFee", p.getBaseFee());
            body.put("freeThreshold", p.getFreeThreshold());
            body.put("islandExtraFee", p.getIslandExtraFee());
        });
        return body;
    }
}
