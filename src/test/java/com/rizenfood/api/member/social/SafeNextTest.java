package com.rizenfood.api.member.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 로그인 뒤 돌아갈 화면. 우리 사이트 안 경로만 허용해야 한다(오픈 리다이렉트 방지).
 * 로그인 직후 가짜 사이트로 튕겨 "다시 로그인하세요"로 비밀번호를 받아내는 수법을 막는다.
 */
class SafeNextTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("바깥 주소나 이상한 값은 마이페이지로 바꾼다")
    @ValueSource(strings = {
            "https://evil.example",
            "//evil.example",
            "/\\evil.example",
            "javascript:alert(1)",
            "evil.example",
            "/api/auth/logout",
            "/ok\r\nLocation: https://evil.example",
    })
    void rejectsExternal(String next) {
        assertThat(SocialAuthController.safeNext(next)).isEqualTo(SocialAuthController.DEFAULT_NEXT);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("사이트 안 경로는 그대로 둔다")
    @ValueSource(strings = {"/cart", "/checkout", "/products/cream-of-rice?x=1", "/mypage"})
    void keepsInternal(String next) {
        assertThat(SocialAuthController.safeNext(next)).isEqualTo(next);
    }
}
