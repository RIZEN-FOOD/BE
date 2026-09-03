package com.rizenfood.api.order.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    /** 관리자 주문 목록 한 줄. */
    public record Summary(
            String orderNo,
            String status,
            String ordererName,
            int itemCount,
            String title,
            int totalAmount,
            String paymentStatus,
            Instant orderedAt,
            Instant paidAt) {
    }

    /**
     * 관리자 주문 상세.
     * 배송 처리를 위해 연락처·주소를 복호화해 그대로 보여준다(사업자 본인).
     */
    public record Detail(
            String orderNo,
            String status,
            String ordererName,
            String ordererPhone,
            String ordererEmail,
            String receiverName,
            String receiverPhone,
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
            List<OrderDtos.ItemView> items,
            PaymentInfo payment,
            DeliveryInfo delivery) {
    }

    public record PaymentInfo(String status, String provider, String method, int amount,
                              Instant approvedAt) {
    }

    public record DeliveryInfo(String status, String carrier, String trackingNo,
                               Instant shippedAt, Instant deliveredAt) {
    }

    /** 상태 변경 요청. */
    public record StatusRequest(
            @NotBlank(message = "상태를 지정해 주세요.") String status) {
    }

    /** 운송장 등록 요청. */
    public record ShipRequest(
            @NotBlank(message = "택배사를 입력해 주세요.")
            @Size(max = 60) String carrier,
            @NotBlank(message = "송장번호를 입력해 주세요.")
            @Pattern(regexp = "^[0-9A-Za-z-]{6,40}$", message = "송장번호 형식이 올바르지 않습니다.")
            String trackingNo) {
    }
}
