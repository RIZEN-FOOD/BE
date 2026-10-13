package com.rizenfood.api.banner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 메인 히어로 — 이미지 한 장 슬라이드 (V41, 2026-10-13). */
public final class HeroImageSlideDtos {

    private HeroImageSlideDtos() {
    }

    /**
     * 공개 응답. 버튼은 문구·주소가 둘 다 있을 때만 값이 있다.
     *
     * @param imageMobileUrl 비어 있으면 휴대폰에서도 PC 사진을 쓴다
     */
    public record PublicItem(
            Long id,
            String imagePcUrl,
            String imageMobileUrl,
            String altText,
            String buttonLabel,
            String linkUrl,
            boolean openNewTab,
            int sortOrder) {
    }

    /** 관리자 목록·상세 — 키와 미리보기 URL 을 함께 준다. */
    public record AdminItem(
            Long id,
            String imagePcKey,
            String imagePcUrl,
            String imageMobileKey,
            String imageMobileUrl,
            String altText,
            String buttonLabel,
            String linkUrl,
            boolean openNewTab,
            int sortOrder,
            boolean enabled) {
    }

    public record SaveRequest(
            @NotBlank(message = "PC 이미지를 올려 주세요.") String imagePcKey,
            String imageMobileKey,

            @NotBlank(message = "이미지 설명을 입력해 주세요. (화면을 못 보는 분에게 읽힙니다)")
            @Size(max = 300) String altText,

            @Size(max = 40, message = "버튼 문구는 40자까지 넣을 수 있습니다.") String buttonLabel,

            // 사이트 안 주소(/로 시작, // 나 /\ 는 다른 사이트로 읽히므로 안 됨) 또는 http(s) 주소만.
            // javascript: 같은 주소를 막는다.
            @Pattern(regexp = "^$|^/(?![/\\\\]).*|^https?://.+",
                    message = "이동할 주소는 /로 시작하는 사이트 주소나 http(s):// 주소만 넣을 수 있습니다.")
            @Size(max = 1000) String linkUrl,

            boolean openNewTab,
            int sortOrder,
            boolean enabled) {
    }

    public record EnabledRequest(boolean enabled) {
    }
}
