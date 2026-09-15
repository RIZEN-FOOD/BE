package com.rizenfood.api.notice.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    /** 공개 목록 한 줄. 본문은 담지 않는다. */
    public record PublicListItem(
            Long id,
            String category,
            String title,
            boolean pinned,
            int viewCount,
            Instant publishedAt) {
    }

    /** 공개 상세 + 이전/다음 글 */
    public record PublicDetail(
            Long id,
            String category,
            String title,
            String bodyHtml,
            int viewCount,
            Instant publishedAt) {
    }

    /** 관리자 목록·상세 */
    public record AdminItem(
            Long id,
            String category,
            String title,
            String bodyHtml,
            boolean pinned,
            int viewCount,
            Instant publishedAt,
            boolean visible,
            boolean publicNow,
            Instant createdAt) {
    }

    public record SaveRequest(
            @Pattern(regexp = "NOTICE|EVENT|INFO", message = "구분이 올바르지 않습니다.")
            String category,

            @NotBlank(message = "제목을 입력해 주세요.") @Size(max = 300) String title,

            /** 에디터 입력. 서버에서 살균한 뒤 저장한다. */
            @NotBlank(message = "내용을 입력해 주세요.") String bodyHtml,

            boolean pinned,

            /**
             * 발행 시각. 미래로 두면 예약 발행이 된다.
             * null 이면 임시저장(공개되지 않음).
             */
            Instant publishedAt,

            boolean visible) {
    }

    public record VisibilityRequest(boolean visible) {
    }
}
