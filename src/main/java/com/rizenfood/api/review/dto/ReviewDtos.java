package com.rizenfood.api.review.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * 공개 후기 한 건.
     * 상품 정보를 같이 담는다 — 사이트 전체 모아보기 페이지에서 어느 상품 후기인지,
     * 어디로 링크할지 알아야 한다.
     */
    public record Item(
            Long id,
            String authorName,
            int rating,
            String content,
            boolean verifiedPurchase,
            boolean sponsored,
            List<String> imageUrls,
            Instant createdAt,
            String productSlug,
            String productName,
            String productThumbnailUrl) {
    }

    public record Stats(double average, long count) {
    }

    public record CreateRequest(
            @NotNull(message = "상품을 선택해 주세요.") Long productId,
            @NotNull(message = "별점을 선택해 주세요.")
            @Min(value = 1, message = "별점은 1~5 사이여야 합니다.")
            @Max(value = 5, message = "별점은 1~5 사이여야 합니다.") Integer rating,
            @NotBlank(message = "후기 내용을 입력해 주세요.")
            @Size(max = 2000, message = "후기는 2000자 이내로 입력해 주세요.") String content,
            /**
             * 업로드된 이미지 키 목록. 업로드는 별도 엔드포인트로 먼저 한다.
             * 우리 업로드 파이프라인이 만든 형식만 받는다 — 아무 문자열이나 넣어
             * 엉뚱한 경로를 후기 사진으로 걸어두지 못하게 한다.
             */
            List<@Pattern(regexp = "^[a-z][a-z0-9-]{0,29}/[0-9]{4}/[0-9]{2}/[0-9a-f]{32}$",
                    message = "사진 정보가 올바르지 않습니다. 사진을 다시 올려 주세요.") String> imageKeys) {
    }

    /** 관리자 목록 한 줄. 숨김 사유·회원 id 등 관리 정보를 더 담는다. */
    public record AdminItem(
            Long id,
            String authorName,
            Long memberId,
            int rating,
            String content,
            boolean visible,
            boolean sponsored,
            String hiddenReason,
            List<String> imageUrls,
            Instant createdAt,
            String productSlug,
            String productName) {
    }

    public record ModerateRequest(boolean visible, String reason) {
    }
}
