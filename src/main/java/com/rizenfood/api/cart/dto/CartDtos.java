package com.rizenfood.api.cart.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CartDtos {

    private CartDtos() {
    }

    /** 한 번에 담을 수 있는 최대 수량. 도매는 B2B 문의로 유도한다. */
    public static final int MAX_QTY = 99;

    public record AddRequest(
            @NotNull(message = "상품을 선택해 주세요.") Long productId,
            /** 옵션 없는 상품이면 null. */
            Long optionId,
            @NotNull(message = "수량을 입력해 주세요.")
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
            @Max(value = MAX_QTY, message = "한 번에 담을 수 있는 수량을 초과했습니다.") Integer quantity) {
    }

    public record UpdateQtyRequest(
            @NotNull(message = "수량을 입력해 주세요.")
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
            @Max(value = MAX_QTY, message = "한 번에 담을 수 있는 수량을 초과했습니다.") Integer quantity) {
    }

    /**
     * 장바구니 한 항목. 금액·재고는 모두 서버가 상품 테이블에서 다시 읽어 채운다.
     *
     * available 이 false 면 결제 대상에서 빠진다. reason 에 사유를 담아
     * 화면이 "품절"·"판매 중지" 같은 안내를 보여줄 수 있게 한다.
     */
    public record ItemView(
            Long id,
            Long productId,
            String slug,
            String name,
            Long optionId,
            String optionName,
            String thumbnailUrl,
            int unitPrice,
            int quantity,
            int lineAmount,
            boolean available,
            int availableStock,
            String reason) {
    }

    /**
     * 장바구니 전체.
     *
     * 금액은 available 항목만 합산한다 — 품절 항목은 결제에 넣지 않으므로
     * 배송비 무료 임계액 계산에서도 빠진다.
     */
    public record CartView(
            List<ItemView> items,
            int totalQuantity,
            int itemsAmount,
            int shippingFee,
            Integer freeShippingThreshold,
            int freeShippingRemaining,
            int totalAmount,
            boolean hasUnavailable) {
    }
}
