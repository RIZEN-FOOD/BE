package com.rizenfood.api.member;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 비밀번호 규칙 (기획서 §6.2).
 *  - 8자 이상
 *  - 영문 + 숫자 조합
 *  - 흔한 비밀번호 차단
 *
 * 규칙 위반 메시지는 사용자에게 그대로 보여줄 수 있게 쓴다.
 */
@Component
public class PasswordPolicy {

    /** 아주 흔한 비밀번호 일부. 사전 공격의 첫 표적이 되는 것들이다. */
    private static final Set<String> COMMON = Set.of(
            "password", "12345678", "123456789", "qwerty123", "qwertyui",
            "password1", "11111111", "00000000", "abc12345", "1q2w3e4r",
            "asdf1234", "password123", "iloveyou", "admin123");

    /** @return 문제가 없으면 null, 있으면 사용자에게 보여줄 사유 */
    public String validate(String raw) {
        if (raw == null || raw.length() < 8) {
            return "비밀번호는 8자 이상이어야 합니다.";
        }
        if (raw.length() > 64) {
            return "비밀번호가 너무 깁니다.";
        }
        boolean hasLetter = raw.chars().anyMatch(Character::isLetter);
        boolean hasDigit = raw.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return "비밀번호는 영문과 숫자를 함께 사용해야 합니다.";
        }
        if (COMMON.contains(raw.toLowerCase())) {
            return "너무 흔한 비밀번호입니다. 다른 비밀번호를 사용해 주세요.";
        }
        return null;
    }
}
