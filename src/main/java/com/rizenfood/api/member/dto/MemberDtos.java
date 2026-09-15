package com.rizenfood.api.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원 인증 API 요청·응답.
 */
public final class MemberDtos {

    private MemberDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 320) String email,

            @NotBlank(message = "비밀번호를 입력해 주세요.") String password,

            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 100) String name,

            /** 선택. 없으면 나중에 마이페이지에서 등록. */
            String phone,

            /** 필수 동의 */
            @AssertTrue(message = "이용약관과 개인정보처리방침에 동의해 주세요.") boolean agreeRequired,

            /** 만 14세 이상 확인 (필수) */
            @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.") boolean ageOver14,

            /** 마케팅 수신 (선택) */
            boolean agreeMarketing) {
    }

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
    }

    public record CheckEmailRequest(
            @NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") String email) {
    }

    /** 로그인·내 정보 응답. 민감정보는 담지 않는다. */
    public record MemberResponse(Long id, String email, String name, String provider) {
    }
}
