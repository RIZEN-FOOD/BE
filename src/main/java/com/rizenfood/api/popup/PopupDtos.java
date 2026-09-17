package com.rizenfood.api.popup;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 팝업 API 요청·응답. */
public final class PopupDtos {

    private PopupDtos() {
    }

    /**
     * 공개 응답.
     *
     * @param version 내용이 바뀌면 달라지는 값. 화면은 "오늘 하루 보지 않기"를 이 값과 함께 기억해,
     *                팝업을 고쳐 올리면 같은 날이라도 다시 보여 준다.
     */
    public record PublicItem(
            Long id,
            String title,
            String imageUrl,
            boolean showHideToday,
            boolean showLinkButton,
            String linkUrl,
            long version) {
    }

    public record AdminItem(
            Long id,
            String title,
            String imageUrl,
            String imageKey,
            boolean showHideToday,
            boolean showLinkButton,
            String linkUrl,
            boolean alwaysOn,
            Instant startAt,
            Instant endAt,
            int sortOrder,
            boolean visible,
            boolean activeNow) {
    }

    /** 사이트 안 주소(/로 시작, //는 제외) 또는 http(s) 주소만 받는다. */
    public static final String LINK_PATTERN = "^$|^https?://[^\\s]+$|^/(?![/\\\\])[^\\s]*$";

    public record SaveRequest(
            @NotBlank(message = "제목을 입력해 주세요.") @Size(max = 200, message = "제목은 200자 이내로 적어 주세요.")
            String title,

            @NotBlank(message = "팝업 이미지를 올려 주세요.")
            @Pattern(regexp = "^[a-z][a-z0-9-]{0,29}/[0-9]{4}/[0-9]{2}/[0-9a-f]{32}$",
                    message = "이미지를 다시 올려 주세요.")
            String imageKey,

            boolean showHideToday,
            boolean showLinkButton,

            @Pattern(regexp = LINK_PATTERN,
                    message = "이동 주소는 https:// 로 시작하는 주소나 / 로 시작하는 사이트 안 주소여야 합니다.")
            @Size(max = 1000)
            String linkUrl,

            boolean alwaysOn,
            Instant startAt,
            Instant endAt,
            boolean visible) {
    }

    public record VisibilityRequest(boolean visible) {
    }

    public record OrderRequest(java.util.List<Long> ids) {
    }
}
