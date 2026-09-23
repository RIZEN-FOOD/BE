package com.rizenfood.api.coupon;

import java.time.Instant;
import java.util.List;

import com.rizenfood.api.order.dto.OrderDtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CouponDtos {

    private CouponDtos() {
    }

    // ── 손님 ──────────────────────────────────────────────────

    /**
     * 결제 화면에서 코드를 눌러 확인할 때.
     * ★ 할인 금액은 받지 않는다. 코드만 받고 서버가 장바구니를 다시 읽어 계산한다.
     */
    public record PreviewRequest(
            @NotBlank(message = "할인코드를 입력해 주세요.")
            @Size(max = 60) String code,

            /**
             * 주문자 연락처. 비워도 된다.
             * 비회원의 "1인 n회"를 결제 전에 미리 알려주려고 받는다. 없으면 그 검사만 건너뛴다.
             */
            @Size(max = 20) String ordererPhone,

            /** «바로 구매» 줄들. 비어 있으면 장바구니 금액으로 본다. */
            @Size(max = 20) List<OrderDtos.@Valid DirectItem> items) {

        public boolean isDirect() {
            return items != null && !items.isEmpty();
        }
    }

    /** 미리보기 결과. 적용했을 때 얼마가 깎이고 얼마를 내는지. */
    public record PreviewResponse(
            String code,
            String name,
            int itemsAmount,
            int shippingFee,
            int discountAmount,
            int totalAmount) {
    }

    // ── 관리자 ────────────────────────────────────────────────

    /**
     * 할인코드 등록·수정.
     *
     * 관리자 화면에서 쓰는 말로 맞춘다 (CLAUDE.md 규칙 4).
     * discountType 은 화면에서 "비율(%)"과 "금액(원)" 중 고르게 한다.
     */
    public record SaveRequest(
            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 200) String name,

            @NotBlank(message = "코드를 입력해 주세요.")
            @Size(min = 2, max = 60)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                     message = "코드는 영문·숫자·하이픈(-)·밑줄(_)만 쓸 수 있습니다.")
            String code,

            @NotBlank
            @Pattern(regexp = "^(PERCENT|AMOUNT)$") String discountType,

            @Min(value = 1, message = "할인 값은 1 이상이어야 합니다.")
            int discountValue,

            /** 비율 할인일 때의 상한. 비워두면 상한 없음. */
            @Min(1) Integer maxDiscount,

            @Min(value = 0, message = "최소 주문금액은 0 이상이어야 합니다.")
            int minOrderAmount,

            /** 전체 사용 한도. 비워두면 무제한. */
            @Min(1) Integer totalQuantity,

            @Min(value = 0, message = "1인 사용 횟수는 0 이상이어야 합니다.")
            @Max(value = 100, message = "1인 사용 횟수가 너무 큽니다.")
            int perMemberLimit,

            @NotNull(message = "시작 일시를 정해 주세요.") Instant startAt,
            @NotNull(message = "종료 일시를 정해 주세요.") Instant endAt,

            boolean visible) {
    }

    /** 관리자 목록·상세 한 줄. 사용 현황을 함께 보여준다. */
    public record AdminItem(
            Long id,
            String name,
            String code,
            String discountType,
            int discountValue,
            Integer maxDiscount,
            int minOrderAmount,
            Integer totalQuantity,
            int usedCount,
            int perMemberLimit,
            Instant startAt,
            Instant endAt,
            boolean visible,
            String state,
            int orderCount,
            long salesAmount,
            long discountTotal) {
    }
}
