package com.rizenfood.api.member;

/**
 * 회원 인증 실패. 메시지는 사용자에게 그대로 보여준다.
 */
public class MemberAuthException extends RuntimeException {

    public MemberAuthException(String message) {
        super(message);
    }
}
