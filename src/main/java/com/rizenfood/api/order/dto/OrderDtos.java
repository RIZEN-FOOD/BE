package com.rizenfood.api.order.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * 주문 생성 요청.
     *
     * ★ 금액·상품·수량은 받지 않는다. 서버가 요청자의 장바구니를 다시 읽어
     *   금액을 계산하고 재고를 확인한다 (CLAUDE.md 규칙 5).
     *   클라이언트는 주문자·배송지 정보만 보낸다.
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
            @Size(max = 300) String deliveryMemo) {
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
            List<ItemView> items) {
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
