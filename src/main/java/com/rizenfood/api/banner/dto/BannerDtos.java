package com.rizenfood.api.banner.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배너 API 요청·응답.
 */
public final class BannerDtos {

    private BannerDtos() {
    }

    /** 공개 응답. 화면이 바로 쓸 수 있게 이미지 URL 로 준다. */
    public record PublicItem(
            Long id,
            String imagePcUrl,
            String imageMobileUrl,
            String altText,
            String linkUrl,
            boolean openNewTab) {
    }

    /** 관리자 목록·상세 */
    public record AdminItem(
            Long id,
            String title,
            String imagePcUrl,
            String imageMobileUrl,
            String imagePcKey,
            String imageMobileKey,
            String altText,
            String linkUrl,
            String position,
            boolean openNewTab,
            boolean alwaysOn,
            Instant startAt,
            Instant endAt,
            int sortOrder,
            boolean visible,
            boolean activeNow) {
    }

    public record SaveRequest(
            @NotBlank(message = "배너 제목을 입력해 주세요.") @Size(max = 200) String title,

            // PC·모바일 이미지를 각각 받는다. 하나라도 없으면 저장하지 않는다.
            @NotBlank(message = "PC 이미지를 올려 주세요.") String imagePcKey,
            @NotBlank(message = "모바일 이미지를 올려 주세요.") String imageMobileKey,

            // 대체 텍스트는 접근성 필수라 비워둘 수 없다.
            @NotBlank(message = "대체 텍스트를 입력해 주세요. (화면을 못 보는 분에게 읽힙니다)")
            @Size(max = 300) String altText,

            @Pattern(regexp = "^$|^https?://.+", message = "링크는 http:// 또는 https:// 로 시작해야 합니다.")
            @Size(max = 1000) String linkUrl,

            @Pattern(regexp = "MAIN_TOP|MAIN_MID|PRODUCT_TOP", message = "노출 위치가 올바르지 않습니다.")
            String position,

            boolean openNewTab,
            boolean alwaysOn,
            Instant startAt,
            Instant endAt,
            boolean visible) {
    }

    public record VisibilityRequest(boolean visible) {
    }
}
