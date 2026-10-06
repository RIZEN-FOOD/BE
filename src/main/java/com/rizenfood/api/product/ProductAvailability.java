package com.rizenfood.api.product;

/**
 * 지금 이 상품(옵션)을 이 수량만큼 팔 수 있는가 — 한 곳에서만 판정한다 (2026-10-06).
 *
 * 자사몰 주문(장바구니·바로구매)과 네이버페이 주문형이 같은 규칙을 쓴다. 규칙이 갈라지면
 * 한쪽 경로로만 품절 상품이 팔린다.
 *
 * ★ 관리자 «수동 품절»(sold_out)도 본다. 전에는 주문 경로가 노출·재고만 봐서, 관리자가 품절로 돌려도
 *   재고가 남아 있으면 요청을 직접 보내 주문할 수 있었다(상품 화면은 품절로 보여 버튼이 막혀 있었다).
 */
public final class ProductAvailability {

    private ProductAvailability() {
    }

    /** 팔 수 없으면 손님에게 보여줄 사유, 팔 수 있으면 null. */
    public static String unavailableReason(Product product, ProductOption option, int qty) {
        boolean visible = product.isVisible() && (option == null || option.isVisible());
        if (!visible) return "판매하지 않는 상품입니다.";
        if (product.isSoldOut()) return "품절되었습니다.";
        int stock = option != null ? option.getStock() : nz(product.getStock());
        if (stock <= 0) return "품절되었습니다.";
        if (stock < qty) return "재고가 부족합니다. 남은 수량 " + stock + "개.";
        return null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
