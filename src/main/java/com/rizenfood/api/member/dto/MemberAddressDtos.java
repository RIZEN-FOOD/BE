package com.rizenfood.api.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원 배송지 DTO.
 *
 * 응답의 휴대폰(receiverPhone)은 본인 소유 데이터이므로 평문으로 준다.
 * (컨트롤러가 본인 회원의 주소만 조회하도록 memberId 로 좁힌다.)
 */
public final class MemberAddressDtos {

    private MemberAddressDtos() {
    }

    public record SaveRequest(
            @Size(max = 50) String label,
            @NotBlank(message = "받는 분을 입력해 주세요.") @Size(max = 100) String receiverName,
            @Size(max = 20) String receiverPhone,
            @NotBlank(message = "우편번호를 입력해 주세요.") @Size(max = 10) String zipcode,
            @NotBlank(message = "주소를 입력해 주세요.") @Size(max = 300) String addr1,
            @Size(max = 300) String addr2,
            boolean makeDefault) {
    }

    public record Response(
            Long id,
            String label,
            String receiverName,
            String receiverPhone,
            String zipcode,
            String addr1,
            String addr2,
            boolean isDefault) {
    }
}
