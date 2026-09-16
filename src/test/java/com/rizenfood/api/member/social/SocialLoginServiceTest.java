package com.rizenfood.api.member.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.rizenfood.api.member.Member;
import com.rizenfood.api.member.MemberAuthException;
import com.rizenfood.api.member.MemberRepository;

/**
 * 간편 로그인으로 들어온 사람을 어떤 계정과 이을지 (결정: 같은 이메일은 자동으로 합치지 않는다).
 */
class SocialLoginServiceTest {

    private MemberRepository repo;
    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        repo = mock(MemberRepository.class);
        service = new SocialLoginService(List.of(), repo, new SocialProperties(null, null, null));
        when(repo.findByProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
        when(repo.findByEmail(any())).thenReturn(List.of());
        when(repo.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SocialProfile kakao(String email, boolean verified) {
        return new SocialProfile("kakao", "12345", email, verified, "홍길동");
    }

    @Test
    @DisplayName("이미 카카오로 가입한 회원은 바로 로그인")
    void existingSocialMemberLogsIn() {
        Member existing = Member.socialMember("KAKAO", "12345", "hong@example.com", "홍길동");
        when(repo.findByProviderAndProviderId("KAKAO", "12345")).thenReturn(Optional.of(existing));

        SocialLoginService.Outcome outcome = service.resolve(kakao("hong@example.com", true));

        assertThat(outcome).isInstanceOf(SocialLoginService.LoggedIn.class);
        assertThat(((SocialLoginService.LoggedIn) outcome).member()).isSameAs(existing);
    }

    @Test
    @DisplayName("같은 이메일의 이메일 가입 계정이 있으면 합치지 않고 기존 방식으로 안내")
    void verifiedEmailOfLocalAccountIsBlocked() {
        Member local = Member.localMember("hong@example.com", "hash", "홍길동");
        when(repo.findByEmail("hong@example.com")).thenReturn(List.of(local));

        SocialLoginService.Outcome outcome = service.resolve(kakao("Hong@Example.com", true));

        assertThat(outcome).isInstanceOf(SocialLoginService.EmailTaken.class);
        assertThat(((SocialLoginService.EmailTaken) outcome).existingProvider()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("확인되지 않은 이메일로는 기존 계정과 대조하지 않는다 (남의 이메일로 막거나 엿보지 못하게)")
    void unverifiedEmailIsIgnored() {
        SocialLoginService.Outcome outcome = service.resolve(kakao("hong@example.com", false));

        assertThat(outcome).isInstanceOf(SocialLoginService.NeedsSignup.class);
        verify(repo, never()).findByEmail(any());
    }

    @Test
    @DisplayName("처음 온 사람은 바로 가입시키지 않고 동의 화면으로")
    void newcomerNeedsConsent() {
        SocialLoginService.Outcome outcome = service.resolve(kakao("new@example.com", true));

        assertThat(outcome).isInstanceOf(SocialLoginService.NeedsSignup.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("이용 정지 계정은 들어올 수 없다")
    void suspendedIsUnavailable() {
        Member existing = Member.socialMember("KAKAO", "12345", "hong@example.com", "홍길동");
        ReflectionTestUtils.setField(existing, "status", "SUSPENDED");
        when(repo.findByProviderAndProviderId("KAKAO", "12345")).thenReturn(Optional.of(existing));

        assertThat(service.resolve(kakao("hong@example.com", true)))
                .isInstanceOf(SocialLoginService.Unavailable.class);
    }

    @Test
    @DisplayName("동의하면 가입 — 이메일이 없으면 연락용이 아닌 자리 표시 주소, 동의 시각 기록")
    void completeSignupWithoutEmail() {
        Member saved = service.completeSignup(kakao(null, false), true, false);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(repo).save(captor.capture());
        assertThat(saved.getProvider()).isEqualTo("KAKAO");
        assertThat(saved.getProviderId()).isEqualTo("12345");
        assertThat(saved.getEmail()).isEqualTo("kakao_12345@social.invalid");
        assertThat(saved.contactEmail()).isNull();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getTermsAgreedAt()).isNotNull();
    }

    @Test
    @DisplayName("만 14세 확인이 없으면 가입하지 않는다")
    void completeSignupRequiresAge() {
        assertThatThrownBy(() -> service.completeSignup(kakao("new@example.com", true), false, false))
                .isInstanceOf(MemberAuthException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("동의 화면에 있는 사이 같은 이메일 계정이 생겼으면 가입하지 않는다")
    void completeSignupRechecksEmail() {
        Member local = Member.localMember("new@example.com", "hash", "다른 사람");
        when(repo.findByEmail("new@example.com")).thenReturn(List.of(local));

        assertThatThrownBy(() -> service.completeSignup(kakao("new@example.com", true), true, false))
                .isInstanceOf(MemberAuthException.class)
                .hasMessageContaining("기존 방식");
        verify(repo, never()).save(any());
    }
}
