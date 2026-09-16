package com.rizenfood.api.member.social;

/**
 * 간편 로그인 중 제공자와의 통신이 실패했을 때.
 *
 * 메시지는 서버 로그용이다. 손님에게는 "잠시 후 다시 시도해 주세요" 수준으로만 알린다
 * — 제공자 응답 내용을 그대로 보여주지 않는다.
 */
public class SocialLoginException extends RuntimeException {

    public SocialLoginException(String message) {
        super(message);
    }

    public SocialLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
