package com.rizenfood.api.naverpay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageVariant;
import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductAvailability;
import com.rizenfood.api.product.ProductOption;
import com.rizenfood.api.product.ProductRepository;
import com.rizenfood.api.shipping.IslandZipService;
import com.rizenfood.api.shipping.ShippingPolicy;
import com.rizenfood.api.shipping.ShippingPolicyRepository;

/**
 * 네이버페이 주문형 (2026-10-06).
 *
 * 흐름: 상품 상세의 [N Pay 구매] → 우리 서버가 주문 정보를 네이버에 등록하고 인증키를 받는다 →
 * 버튼 SDK 가 그 키로 네이버페이 주문서를 연다 → 손님이 네이버에서 결제한다.
 * 결제된 주문은 주문 관리 API(가맹 승인 뒤 연동)로 가져와 재고를 차감하고 송장을 넘긴다.
 *
 * ★ 금액을 화면에서 받지 않는다 (CLAUDE.md 규칙 5). 화면은 «무엇을 몇 개»만 보내고,
 *   가격·배송비·재고는 여기서 DB 를 다시 읽어 네이버에 넘긴다.
 * ★ 지금은 옵션 없는 단일 상품만 받는다. 옵션 상품은 버튼을 띄우지 않고, 와도 거절한다.
 */
@Service
public class NaverPayService {

    private static final Logger log = LoggerFactory.getLogger(NaverPayService.class);

    /** 네이버페이 수량 상한 (가이드 표 2-5: 1,000개 이상이면 경고). */
    static final int MAX_QUANTITY = 999;

    private final NaverPayProperties props;
    private final NaverPayClient client;
    private final ProductRepository products;
    private final ShippingPolicyRepository shippingPolicies;
    private final IslandZipService islandZips;
    private final ImageService images;
    private final String siteUrl;

    public NaverPayService(NaverPayProperties props, NaverPayClient client, ProductRepository products,
                           ShippingPolicyRepository shippingPolicies, IslandZipService islandZips,
                           ImageService images, @Value("${app.site-url:}") String siteUrl) {
        this.props = props;
        this.client = client;
        this.products = products;
        this.shippingPolicies = shippingPolicies;
        this.islandZips = islandZips;
        this.images = images;
        this.siteUrl = siteUrl == null ? "" : siteUrl.trim().replaceAll("/+$", "");
    }

    /** 화면에 내보내는 공개 설정. 버튼 키·공통 키는 원래 페이지에 실리는 값이다. 가맹점 인증키는 내보내지 않는다. */
    public record PublicConfig(boolean enabled, String buttonKey, String scriptUrl, String commonKey) {
        static final PublicConfig OFF = new PublicConfig(false, null, null, null);
    }

    /**
     * 켜져 있고 키가 다 있으며, 네이버에 넘길 절대 주소를 만들 수 있는가.
     * 사이트 주소(app.site-url)가 비면 상품·이미지 주소를 만들 수 없어 끈 것으로 본다.
     */
    public boolean ready() {
        return props.ready() && !siteUrl.isEmpty();
    }

    public PublicConfig publicConfig() {
        if (!ready()) {
            return PublicConfig.OFF;
        }
        return new PublicConfig(true, props.buttonKey(), props.buttonScriptUrl(),
                props.commonKey().isEmpty() ? null : props.commonKey());
    }

    /**
     * 주문 정보를 네이버에 등록하고 주문서를 열 인증키를 받는다.
     *
     * @throws IllegalArgumentException 팔 수 없는 상품·수량 (손님에게 사유를 보여준다)
     * @throws NaverPayException        네이버 거절·통신 실패
     */
    @Transactional(readOnly = true)
    public NaverPayRegisterResult register(long productId, int quantity, NaverPayXml.Inflow inflow) {
        requireReady();
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 1개부터 " + MAX_QUANTITY + "개까지 주문할 수 있습니다.");
        }
        Product p = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
        if (hasVisibleOptions(p)) {
            throw new IllegalArgumentException("옵션을 고르는 상품은 아직 네이버페이로 주문할 수 없습니다.");
        }
        String reason = ProductAvailability.unavailableReason(p, null, quantity);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }

        NaverPayXml.ProductInfo info = productInfo(p, currentPolicy());
        String xml = NaverPayXml.order(props.merchantId(), props.certiKey(), info, quantity, info.infoUrl(), inflow);
        String body = client.post(props.registerUrl(), xml);
        try {
            return NaverPayRegisterResult.parse(body);
        } catch (NaverPayException e) {
            // 거절 사유(에러코드)는 운영에서 원인을 찾는 단서다. 손님 연락처 같은 개인정보는 들어 있지 않다.
            log.warn("네이버페이 주문 등록 실패: product={} qty={} — {}", productId, quantity, e.getMessage());
            throw e;
        }
    }

    /** 네이버가 상품 정보를 확인하러 부른다(가이드 3.2). 모르는 상품 번호는 건너뛴다. */
    @Transactional(readOnly = true)
    public String productInfoXml(String rawQuery) {
        requireReady();
        ShippingPolicy policy = currentPolicy();
        List<NaverPayXml.ProductInfo> list = new ArrayList<>();
        for (String id : NaverPayQuery.productInfoIds(rawQuery)) {
            Long pid = parseId(id);
            if (pid == null) {
                continue;
            }
            products.findById(pid).ifPresent(p -> list.add(productInfo(p, policy)));
        }
        return NaverPayXml.products(list);
    }

    /**
     * 손님이 네이버 주문서에 배송지를 넣으면 네이버가 도서산간 추가 배송비를 물어본다(가이드 3.3).
     * 우리 도서산간 우편번호 목록(관리자 사이트 설정)과 배송비 정책의 추가 금액으로 판정한다.
     */
    @Transactional(readOnly = true)
    public String additionalFeeXml(String rawQuery) {
        requireReady();
        String zipcode = NaverPayQuery.param(rawQuery, "zipcode");
        int extra = zipcode != null && islandZips.isIsland(zipcode.trim())
                ? currentPolicy().getIslandExtraFee()
                : 0;
        Map<String, Integer> fees = new LinkedHashMap<>();
        for (String id : NaverPayQuery.additionalFeeIds(rawQuery)) {
            fees.put(id, extra);
        }
        return NaverPayXml.additionalFees(fees);
    }

    // ── 내부 ─────────────────────────────────────────────

    NaverPayXml.ProductInfo productInfo(Product p, ShippingPolicy policy) {
        String status;
        if (!p.isVisible() || hasVisibleOptions(p)) {
            status = "NOT_SALE"; // 옵션 상품은 아직 네이버페이로 팔지 않는다
        } else if (ProductAvailability.unavailableReason(p, null, 1) != null) {
            status = "SOLD_OUT";
        } else {
            status = "ON_SALE";
        }
        String name = p.getNameKo() == null ? "" : p.getNameKo().trim();
        if (name.length() > 100) {
            name = name.substring(0, 100); // 최대 100자 (표 3-2)
        }
        return new NaverPayXml.ProductInfo(
                p.getId(),
                name,
                Math.max(1, p.effectivePrice()),
                props.taxType(),
                siteUrl + "/products/" + p.getSlug(),
                imageUrl(p),
                status,
                p.getStock() == null ? 0 : Math.max(0, p.getStock()),
                policy.getReturnFee(),
                policy.getExchangeFee(),
                shippingOf(policy));
    }

    /**
     * 우리 배송비 정책 → 네이버 배송비 정책.
     * 한 주문에 배송비 한 번이라 모든 상품을 같은 묶음 그룹(정책 id)에 넣는다.
     */
    static NaverPayXml.Shipping shippingOf(ShippingPolicy policy) {
        String groupId = String.valueOf(policy.getId());
        boolean surcharge = policy.getIslandExtraFee() > 0;
        int base = policy.getBaseFee();
        if (base <= 0) {
            return new NaverPayXml.Shipping(groupId, "FREE", "FREE", 0, null, surcharge);
        }
        Integer threshold = policy.getFreeThreshold();
        if (threshold != null && threshold > 0) {
            return new NaverPayXml.Shipping(groupId, "CONDITIONAL_FREE", "PREPAYED", base, threshold, surcharge);
        }
        return new NaverPayXml.Shipping(groupId, "CHARGE", "PREPAYED", base, null, surcharge);
    }

    private String imageUrl(Product p) {
        String key = p.getThumbnailKey();
        if (key == null || key.isBlank()) {
            // 이미지가 없는 상품도 주소는 있어야 한다(필수 항목). 브랜드 기본 이미지로 대신한다.
            return siteUrl + "/assets/brand/og-default.jpg";
        }
        String url = images.urlOf("%s_%s.webp".formatted(key, ImageVariant.LARGE.suffix()));
        return url.startsWith("/") ? siteUrl + url : url;
    }

    private static boolean hasVisibleOptions(Product p) {
        return p.getOptions().stream().anyMatch(ProductOption::isVisible);
    }

    private ShippingPolicy currentPolicy() {
        return shippingPolicies.findFirstByVisibleTrueOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("활성 배송비 정책이 없습니다."));
    }

    private void requireReady() {
        if (!ready()) {
            throw new NotFoundException("네이버페이를 지금은 쓸 수 없습니다.");
        }
    }

    private static Long parseId(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
