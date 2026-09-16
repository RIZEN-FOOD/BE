package com.rizenfood.api.member.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.rizenfood.api.security.JwtProperties;
import com.rizenfood.api.security.JwtTokenProvider;

/**
 * 간편 로그인 중에 들고 다니는 서명 값(state·가입 티켓).
 * 서로, 그리고 로그인 토큰과 섞여 쓰일 수 없어야 한다.
 */
class SocialTokenTest {

    private final JwtTokenProvider tokens = new JwtTokenProvider(new JwtProperties(
            "test-only-jwt-secret-at-least-32-bytes-long", 240, 30, 14, false, "Lax"));

    @Test
    @DisplayName("state 는 만든 그대로 읽힌다")
    void stateRoundTrip() {
        String token = tokens.createOAuthState("kakao", "abc", "/cart");

        var state = tokens.parseOAuthState(token).orElseThrow();
        assertThat(state.provider()).isEqualTo("kakao");
        assertThat(state.state()).isEqualTo("abc");
        assertThat(state.next()).isEqualTo("/cart");
    }

    @Test
    @DisplayName("가입 티켓은 만든 그대로 읽힌다")
    void ticketRoundTrip() {
        String token = tokens.createSocialSignupTicket("naver", "n-1", "a@b.com", true, "홍길동", "/mypage");

        var t = tokens.parseSocialSignupTicket(token).orElseThrow();
        assertThat(t.provider()).isEqualTo("naver");
        assertThat(t.providerId()).isEqualTo("n-1");
        assertThat(t.email()).isEqualTo("a@b.com");
        assertThat(t.emailVerified()).isTrue();
        assertThat(t.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("용도가 다른 값은 서로 대신 쓸 수 없다")
    void audiencesAreSeparated() {
        String state = tokens.createOAuthState("kakao", "abc", "/cart");
        String ticket = tokens.createSocialSignupTicket("kakao", "1", null, false, "홍", "/");
        String member = tokens.createMemberAccessToken(1L, "홍");

        assertThat(tokens.parseSocialSignupTicket(state)).isEmpty();
        assertThat(tokens.parseOAuthState(ticket)).isEmpty();
        assertThat(tokens.parseSocialSignupTicket(member)).isEmpty();
        // 가입 티켓으로 회원 행세를 할 수 없다
        assertThat(tokens.parseMemberToken(ticket)).isEmpty();
    }

    @Test
    @DisplayName("한 글자라도 바꾸면 거부된다")
    void tamperedIsRejected() {
        String ticket = tokens.createSocialSignupTicket("kakao", "1", null, false, "홍", "/");
        String tampered = ticket.substring(0, ticket.length() - 2) + (ticket.endsWith("A") ? "BB" : "AA");

        assertThat(tokens.parseSocialSignupTicket(tampered)).isEmpty();
        assertThat(tokens.parseSocialSignupTicket("")).isEmpty();
        assertThat(tokens.parseSocialSignupTicket(null)).isEmpty();
    }
}
