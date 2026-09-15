package com.rizenfood.api.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 도서산간 우편번호 판정. */
class IslandZipRangesTest {

    private final IslandZipRanges defaults = IslandZipRanges.DEFAULT;

    @Test
    @DisplayName("제주 범위의 양 끝은 도서산간, 바로 밖은 아니다")
    void jejuBoundaries() {
        assertThat(defaults.contains("63000")).isTrue();
        assertThat(defaults.contains("63644")).isTrue();
        assertThat(defaults.contains("62999")).isFalse();
        assertThat(defaults.contains("63645")).isFalse();
    }

    @Test
    @DisplayName("울릉·옹진·신안은 도서산간, 서울·충주는 아니다")
    void mainlandVsIsland() {
        assertThat(defaults.contains("40220")).isTrue();  // 울릉
        assertThat(defaults.contains("23110")).isTrue();  // 옹진
        assertThat(defaults.contains("58850")).isTrue();  // 신안
        assertThat(defaults.contains("06234")).isFalse(); // 서울 강남
        assertThat(defaults.contains("27459")).isFalse(); // 충주
    }

    @Test
    @DisplayName("형식이 틀린 우편번호는 도서산간이 아니다")
    void malformedZip() {
        assertThat(defaults.contains(null)).isFalse();
        assertThat(defaults.contains("")).isFalse();
        assertThat(defaults.contains("6300")).isFalse();
        assertThat(defaults.contains("630000")).isFalse();
        assertThat(defaults.contains("63-00")).isFalse();
        assertThat(defaults.contains(" 63000 ")).isTrue();
    }

    @Test
    @DisplayName("관리자 입력: 쉼표·줄바꿈·물결표를 받고, 틀린 조각은 버린다")
    void parseCustomSpec() {
        IslandZipRanges r = IslandZipRanges.parse("12345,\n20010~20000  abc, 1234-5, 99999");
        assertThat(r.isEmpty()).isFalse();
        assertThat(r.contains("12345")).isTrue();
        assertThat(r.contains("20005")).isTrue(); // 거꾸로 적어도 범위로 본다
        assertThat(r.contains("99999")).isTrue();
        assertThat(r.contains("63000")).isFalse(); // 기본 목록과 섞이지 않는다
    }

    @Test
    @DisplayName("읽을 수 있는 번호가 없으면 비어 있다 (서비스가 기본 목록으로 대체)")
    void emptyWhenNothingValid() {
        assertThat(IslandZipRanges.parse("제주, abc").isEmpty()).isTrue();
        assertThat(IslandZipRanges.parse(null).isEmpty()).isTrue();
    }
}
