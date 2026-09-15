package com.rizenfood.api.order.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    /** 고객의 취소·반품·교환 신청. */
    public record CreateRequest(
            @NotBlank(message = "요청 종류를 선택해 주세요.") String type, // CANCEL | RETURN | EXCHANGE
            @NotBlank(message = "사유를 선택해 주세요.") @Size(max = 40) String reasonCode,
            @Size(max = 1000) String reasonText) {
    }

    /** 관리자 처리. */
    public record ProcessRequest(
            @NotBlank(message = "처리 상태를 선택해 주세요.") String status, // APPROVED | REJECTED | COMPLETED
            @Size(max = 1000) String adminMemo,
            Integer refundAmount) {
    }

    /** 고객·관리자 공용 뷰. */
    public record View(
            Long id,
            String orderNo,
            String type,
            String reasonCode,
            String reasonText,
            String status,
            Integer refundAmount,
            String adminMemo,
            Instant requestedAt,
            Instant processedAt) {
    }

    /** 관리자 목록 한 줄(주문자·상품 정보 포함). */
    public record AdminItem(
            Long id,
            String orderNo,
            String ordererName,
            String type,
            String reasonCode,
            String status,
            int orderTotal,
            Instant requestedAt,
            Instant processedAt) {
    }
}
