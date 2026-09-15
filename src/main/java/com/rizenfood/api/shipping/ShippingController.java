package com.rizenfood.api.shipping;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final IslandZipService islandZipService;

    public ShippingController(ShippingPolicyRepository repository, IslandZipService islandZipService) {
        this.repository = repository;
        this.islandZipService = islandZipService;
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

    /**
     * 주문서 미리보기용: 이 우편번호에 도서산간 추가 배송비가 붙는지.
     * 화면 표시용일 뿐이다 — 실제 금액은 주문 생성 때 서버가 다시 계산한다.
     */
    @GetMapping("/island")
    public Map<String, Object> island(@RequestParam String zipcode) {
        int extra = islandZipService.isIsland(zipcode)
                ? repository.findFirstByVisibleTrueOrderByIdAsc()
                        .map(ShippingPolicy::getIslandExtraFee).orElse(0)
                : 0;
        return Map.of("island", extra > 0, "extraFee", extra);
    }
}
