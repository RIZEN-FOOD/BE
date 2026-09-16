package com.rizenfood.api.member.social;

/**
 * 카카오·네이버에서 받아온 회원 정보. 우리가 쓰는 값만 추린다.
 *
 * @param provider      "kakao" | "naver"
 * @param providerId    제공자 안에서의 회원 고유번호. 이메일은 바뀔 수 있어 이 값으로 사람을 식별한다.
 * @param email         동의를 받았을 때만 온다. 없을 수 있다.
 * @param emailVerified 제공자가 본인 소유를 확인한 이메일인지. 확인 안 된 이메일은 계정 대조에 쓰지 않는다.
 * @param name          이름(없으면 별명). 표시용.
 */
public record SocialProfile(
        String provider,
        String providerId,
        String email,
        boolean emailVerified,
        String name) {

    /** 계정 대조에 써도 되는 이메일. 확인되지 않았거나 없으면 null. */
    public String trustedEmail() {
        if (!emailVerified || email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
