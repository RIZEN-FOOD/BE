package com.rizenfood.api.banner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.rizenfood.api.banner.dto.HeroImageSlideDtos;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 이미지 슬라이드 버튼의 이동 주소 (V41, 2026-10-13).
 * 사이트 안 주소와 http(s) 만 받고, 다른 사이트로 읽히는 // · /\ 와 javascript: 는 막는다.
 */
class HeroImageSlideLinkTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean valid(String link) {
        HeroImageSlideDtos.SaveRequest r = new HeroImageSlideDtos.SaveRequest(
                "banners/2026/10/abc", null, "설명", "자세히 보기", link, false, 0, true);
        return validator.validate(r).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/products/cream-of-rice", "/notice/3?x=1", "https://www.rizenfoods.com/event"})
    @DisplayName("사이트 안 주소·http(s) 주소는 받는다")
    void acceptsSafeLinks(String link) {
        assertThat(valid(link)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//evil.example", "/\\evil.example", "products/x", "ftp://x"})
    @DisplayName("다른 사이트로 읽히거나 위험한 주소는 막는다")
    void rejectsUnsafeLinks(String link) {
        assertThat(valid(link)).isFalse();
    }
}
