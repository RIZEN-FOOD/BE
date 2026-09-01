package com.rizenfood.api.inquiry.dto;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InquiryDtos {

    private InquiryDtos() {
    }

    public record CreateRequest(
            @Pattern(regexp = "GENERAL|WHOLESALE|PARTNERSHIP|ORDER", message = "문의 유형이 올바르지 않습니다.")
            String type,

            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 100) String name,

            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 320) String email,

            String phone,

            @NotBlank(message = "문의 내용을 입력해 주세요.")
            @Size(max = 2000, message = "문의는 2000자 이내로 입력해 주세요.") String message,

            /** 개인정보 수집 동의. 체크 안 하면 접수되지 않는다. */
            @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.") boolean agreeConsent) {
    }

    /** 본인·관리자 공용 조회 응답 */
    public record Item(
            Long id,
            String type,
            String name,
            String message,
            String answer,
            Instant answeredAt,
            String status,
            Instant createdAt) {
    }

    /** 관리자 목록은 이메일까지 본다(본인 조회 응답과 분리) */
    public record AdminItem(
            Long id,
            String type,
            String name,
            String email,
            String message,
            String answer,
            Instant answeredAt,
            String status,
            Instant createdAt) {
    }

    public record AnswerRequest(
            @NotBlank(message = "답변 내용을 입력해 주세요.")
            @Size(max = 2000) String answer) {
    }
}
