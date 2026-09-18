package com.rizenfood.api.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 메인 FEATURES API 요청·응답.
 */
public final class MainFeatureDtos {

    private MainFeatureDtos() {
    }

    /**
     * 공개 응답.
     *
     * @param useNutritionBody 본문이 비어 있을 때 화면이 영양성분으로 문장을 만들지 여부
     */
    public record PublicItem(
            Long id,
            String title,
            String body,
            String imageUrl,
            /** 모바일 전용 사진. 없으면 null — 화면은 imageUrl 을 그대로 쓴다. */
            String imageMobileUrl,
            String altText,
            boolean useNutritionBody) {
    }

    /** 관리자 목록. 사진은 미리보기 URL 과 저장 키를 함께 준다. */
    public record AdminItem(
            Long id,
            String title,
            String body,
            String imageKey,
            String imageUrl,
            String imageMobileKey,
            String imageMobileUrl,
            String altText,
            boolean autoNutritionBody,
            int sortOrder,
            boolean visible) {
    }

    public record SaveRequest(
            @NotBlank(message = "큰 문구를 입력해 주세요.")
            @Size(max = 100, message = "큰 문구는 100자까지 넣을 수 있습니다.")
            String title,

            @Size(max = 500, message = "작은 문구는 500자까지 넣을 수 있습니다.")
            String body,

            @Size(max = 500) String imageKey,

            /** 모바일 전용 사진(선택). 비면 PC 사진을 그대로 쓴다. */
            @Size(max = 500) String imageMobileKey,

            @Size(max = 300, message = "사진 설명은 300자까지 넣을 수 있습니다.")
            String altText,

            boolean autoNutritionBody,
            boolean visible) {
    }

    public record VisibilityRequest(boolean visible) {
    }

    /** 순서 바꾸기. 화면에 보이는 차례대로 id 를 보낸다. */
    public record OrderRequest(java.util.List<Long> ids) {
    }
}
