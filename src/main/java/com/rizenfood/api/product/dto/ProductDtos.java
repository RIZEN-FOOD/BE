package com.rizenfood.api.product.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 상품 API 의 요청·응답 형태.
 *
 * 엔티티를 그대로 내보내지 않는다.
 * 내보내면 내부 구조가 공개 계약이 되고, 컬럼 하나 바꿀 때마다 프론트가 깨진다.
 * 숨겨야 할 필드가 실수로 새어 나가는 경로이기도 하다.
 */
public final class ProductDtos {

    private ProductDtos() {
    }

    // ── 공개 응답 ────────────────────────────────────────────

    /** 목록 한 줄. 상세 정보는 담지 않는다. */
    public record ListItem(
            Long id,
            String slug,
            String nameKo,
            String nameEn,
            String subtitle,
            int price,
            Integer discountPrice,
            int effectivePrice,
            Integer weightG,
            boolean soldOut,
            boolean featured,
            String thumbnailUrl) {
    }

    public record ImageItem(String url, String altText, String type) {
    }

    public record OptionItem(Long id, String name, int price, int stock, boolean soldOut) {
    }

    public record NutritionItem(
            BigDecimal servingSizeG, BigDecimal kcal, BigDecimal carbG,
            BigDecimal proteinG, BigDecimal fatG, BigDecimal sugarG, BigDecimal sodiumMg) {
    }

    public record IngredientItem(String name, BigDecimal percentage, String origin, String allergen) {
    }

    public record LabelItem(
            String foodType, String shelfLife, String storageMethod,
            String manufacturer, String manufacturerAddr,
            String seller, String sellerAddr,
            String customerService, String packageMaterial, String extraNotice) {
    }

    public record PurchaseLinkItem(String channel, String url, String label) {
    }

    /** 상세. 법정 표시사항이 전부 텍스트로 나간다. */
    public record Detail(
            Long id,
            String slug,
            String nameKo,
            String nameEn,
            String subtitle,
            String descriptionHtml,
            int price,
            Integer discountPrice,
            int effectivePrice,
            Integer weightG,
            Integer servings,
            int stock,
            boolean soldOut,
            List<ImageItem> images,
            List<OptionItem> options,
            NutritionItem nutrition,
            List<IngredientItem> ingredients,
            LabelItem label,
            List<PurchaseLinkItem> purchaseLinks) {
    }

    // ── 관리자 요청 ──────────────────────────────────────────

    public record ImageRequest(
            @NotBlank(message = "이미지를 선택해 주세요.") String imageKey,
            @Size(max = 300) String altText,
            @Pattern(regexp = "MAIN|DETAIL|LIFESTYLE", message = "이미지 종류가 올바르지 않습니다.")
            String type,
            int sortOrder) {
    }

    public record OptionRequest(
            @NotBlank(message = "옵션 이름을 입력해 주세요.") @Size(max = 120) String name,
            int priceDelta,
            @Min(value = 0, message = "재고는 0 이상이어야 합니다.") int stock,
            int sortOrder,
            boolean visible) {
    }

    public record NutritionRequest(
            @NotNull(message = "1회 제공량을 입력해 주세요.") BigDecimal servingSizeG,
            BigDecimal kcal, BigDecimal carbG, BigDecimal proteinG,
            BigDecimal fatG, BigDecimal sugarG, BigDecimal sodiumMg) {
    }

    public record IngredientRequest(
            @NotBlank(message = "원재료명을 입력해 주세요.") @Size(max = 200) String name,
            BigDecimal percentage,
            @Size(max = 120) String origin,
            @Size(max = 200) String allergen,
            int sortOrder) {
    }

    public record LabelRequest(
            @Size(max = 120) String foodType,
            @Size(max = 200) String shelfLife,
            @Size(max = 300) String storageMethod,
            @Size(max = 200) String manufacturer,
            @Size(max = 300) String manufacturerAddr,
            @Size(max = 200) String seller,
            @Size(max = 300) String sellerAddr,
            @Size(max = 120) String customerService,
            @Size(max = 200) String packageMaterial,
            String extraNotice) {
    }

    public record PurchaseLinkRequest(
            @Pattern(regexp = "NAVER|COUPANG|OWN|OTHER", message = "판매 채널이 올바르지 않습니다.")
            String channel,
            // javascript: 같은 스킴을 막는다.
            @Pattern(regexp = "^https?://.+", message = "구매 링크는 http:// 또는 https:// 로 시작해야 합니다.")
            @Size(max = 1000) String url,
            @Size(max = 120) String label,
            int sortOrder,
            boolean visible) {
    }

    /** 상품 등록·수정 */
    public record SaveRequest(
            @NotBlank(message = "주소에 쓸 영문 이름을 입력해 주세요.")
            @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                    message = "주소에 쓸 이름은 영문 소문자, 숫자, 하이픈만 쓸 수 있습니다.")
            @Size(max = 120) String slug,

            @NotBlank(message = "상품명을 입력해 주세요.") @Size(max = 200) String nameKo,
            @Size(max = 200) String nameEn,
            @Size(max = 300) String subtitle,

            /** 에디터 입력. 서버에서 살균한 뒤 저장한다. */
            String descriptionHtml,

            @NotNull(message = "가격을 입력해 주세요.")
            @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Integer price,
            @Min(value = 0, message = "할인가는 0 이상이어야 합니다.") Integer discountPrice,

            Integer weightG,
            Integer servings,
            @Min(value = 0, message = "재고는 0 이상이어야 합니다.") Integer stock,

            String thumbnailKey,
            boolean featured,
            boolean visible,

            @Valid List<ImageRequest> images,
            @Valid List<OptionRequest> options,
            @Valid NutritionRequest nutrition,
            @Valid List<IngredientRequest> ingredients,
            @Valid LabelRequest label,
            @Valid List<PurchaseLinkRequest> purchaseLinks) {
    }

    /** 드래그 정렬 결과 */
    public record ReorderRequest(@NotNull List<Long> orderedIds) {
    }

    /** 노출 · 메인노출 토글 */
    public record VisibilityRequest(Boolean visible, Boolean featured) {
    }
}
