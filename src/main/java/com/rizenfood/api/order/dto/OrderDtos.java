package com.rizenfood.api.order.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * «바로 구매» 한 줄 — 무엇을 몇 개. 상품 페이지에서 장바구니를 거치지 않고 올 때 쓴다.
     * ★ 가격은 없다. 서버가 상품 테이블에서 다시 읽는다. 장바구니 담기와 같은 신뢰 수준이다.
     */
    public record DirectItem(
            @NotNull(message = "상품을 선택해 주세요.") Long productId,
            Long optionId,
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
            @Max(value = 99, message = "수량은 99개까지 담을 수 있습니다.")
            int quantity) {
    }

    /** «바로 구매» 견적 요청 — 주문서에 보여줄 상품·금액을 서버가 계산해 준다. */
    public record QuoteRequest(
            @NotEmpty(message = "상품을 선택해 주세요.")
            @Size(max = 20) List<@Valid DirectItem> items) {
    }

    /**
     * 주문 생성 요청.
     *
     * ★ 금액은 받지 않는다. 서버가 상품 테이블을 다시 읽어 금액을 계산하고 재고를 확인한다
     *   (CLAUDE.md 규칙 5). 클라이언트는 주문자·배송지 정보만 보낸다.
     *   상품은 두 갈래다 — items 가 비어 있으면 요청자의 장바구니에서, 있으면 그 줄들로만
     *   («바로 구매»). 어느 쪽이든 «무엇을 몇 개»까지만 받고 가격은 받지 않는다.
     */
    public record CreateRequest(
            @NotBlank(message = "주문자 이름을 입력해 주세요.")
            @Size(max = 100) String ordererName,
            @NotBlank(message = "주문자 연락처를 입력해 주세요.")
            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
            String ordererPhone,
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 320) String ordererEmail,

            @NotBlank(message = "받는 분 이름을 입력해 주세요.")
            @Size(max = 100) String receiverName,
            @NotBlank(message = "받는 분 연락처를 입력해 주세요.")
            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
            String receiverPhone,
            @NotBlank(message = "우편번호를 입력해 주세요.")
            @Size(max = 10) String zipcode,
            @NotBlank(message = "주소를 입력해 주세요.")
            @Size(max = 300) String addr1,
            @Size(max = 300) String addr2,
            @Size(max = 300) String deliveryMemo,

            /**
             * 할인코드. 비워도 된다.
             * ★ 할인 금액은 받지 않는다. 코드만 받고 서버가 다시 계산한다.
             */
            @Size(max = 60) String couponCode,

            /** «바로 구매» 줄들. 비어 있으면 장바구니 주문이다. */
            @Size(max = 20) List<@Valid DirectItem> items) {

        /** 장바구니가 아니라 요청에 실린 줄로 주문하는가. */
        public boolean isDirect() {
            return items != null && !items.isEmpty();
        }
    }

    /** 결제(모의) 요청. 금액은 서버가 정하므로 받지 않는다. */
    public record PayRequest(String method) {
    }

    public record ItemView(
            Long productId,
            String slug,
            String name,
            String optionName,
            String thumbnailUrl,
            int unitPrice,
            int quantity,
            int lineAmount) {
    }

    /**
     * 주문 상세.
     * 연락처는 마스킹해서 내려준다 (010-****-1234). 원문은 서버에만 있다.
     */
    public record OrderView(
            String orderNo,
            String status,
            String ordererName,
            String ordererPhoneMasked,
            String ordererEmail,
            String receiverName,
            String receiverPhoneMasked,
            String zipcode,
            String addr1,
            String addr2,
            String deliveryMemo,
            int itemsAmount,
            int shippingFee,
            int discountAmount,
            int totalAmount,
            Instant orderedAt,
            Instant paidAt,
            List<ItemView> items,
            /** 운송장. 아직 안 나왔으면 null. */
            DeliveryView delivery) {
    }

    /**
     * 손님에게 보여줄 배송 정보.
     *
     * @param trackingUrl 택배사 조회 화면 주소. 설정이 비었거나 송장이 없으면 null.
     */
    public record DeliveryView(
            String status,
            String carrier,
            String trackingNo,
            String trackingUrl,
            Instant shippedAt,
            Instant deliveredAt) {
    }

    /**
     * 비회원 주문 조회 요청.
     *
     * 주문번호만으로는 열어주지 않는다 — 받는 분 연락처가 함께 맞아야 한다.
     * 주문번호가 이미 추측 불가능하지만, 링크가 새어나갔을 때를 한 겹 더 막는다.
     */
    public record GuestLookupRequest(
            @NotBlank(message = "주문번호를 입력해 주세요.")
            @Size(max = 40) String orderNo,
            @NotBlank(message = "받는 분 연락처를 입력해 주세요.")
            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
            String receiverPhone) {
    }

    /** 주문 목록 한 줄(마이페이지). */
    public record OrderSummary(
            String orderNo,
            String status,
            String title,
            int itemCount,
            int totalAmount,
            String thumbnailUrl,
            Instant orderedAt) {
    }
}
