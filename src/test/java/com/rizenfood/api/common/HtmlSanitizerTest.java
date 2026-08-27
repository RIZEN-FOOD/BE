package com.rizenfood.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 살균기가 실제로 막아주는지 확인한다.
 * 여기가 깨지면 상품 설명·공지 본문에 저장형 XSS 가 들어간다.
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("코드 실행을 노리는 입력은 전부 제거된다")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">클릭</a>",
            "<iframe src=\"https://evil.example\"></iframe>",
            "<svg/onload=alert(1)>",
            "<body onload=alert(1)>",
            "<div style=\"background:url(javascript:alert(1))\">x</div>",
            "<object data=\"evil.swf\"></object>",
            "<form action=\"https://evil.example\"><input name=\"pw\"></form>",
            "<a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">x</a>",
            "<META HTTP-EQUIV=\"refresh\" CONTENT=\"0;url=javascript:alert(1)\">",
    })
    void 위험한_입력은_제거된다(String dirty) {
        String clean = sanitizer.clean(dirty);

        assertThat(clean.toLowerCase())
                .doesNotContain("<script")
                .doesNotContain("javascript:")
                .doesNotContain("onerror")
                .doesNotContain("onload")
                .doesNotContain("<iframe")
                .doesNotContain("<object")
                .doesNotContain("<form")
                .doesNotContain("<input")
                .doesNotContain("<meta")
                .doesNotContain("style=");
    }

    @Test
    @DisplayName("정상적인 상품 설명 서식은 살아남는다")
    void 허용된_서식은_유지된다() {
        String dirty = """
                <h2>크림오브라이스</h2>
                <p>곱게 도정한 <strong>쌀 100%</strong>입니다.</p>
                <ul><li>물이나 우유에 풀어 드세요</li><li>90초</li></ul>
                <img src="https://cdn.example/products/a.webp" alt="제품 이미지">
                """;

        String clean = sanitizer.clean(dirty);

        assertThat(clean)
                .contains("<h2>")
                .contains("<strong>")
                .contains("<ul>")
                .contains("<li>")
                .contains("<img")
                .contains("alt=\"제품 이미지\"")
                .contains("쌀 100%");
    }

    @Test
    @DisplayName("외부 링크에는 rel 이 붙는다")
    void 외부_링크에_rel_이_붙는다() {
        String clean = sanitizer.clean("<a href=\"https://smartstore.naver.com/x\">네이버</a>");

        assertThat(clean).contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void null_과_빈값을_다룬다() {
        assertThat(sanitizer.clean(null)).isNull();
        assertThat(sanitizer.clean("   ")).isEmpty();
    }
}
