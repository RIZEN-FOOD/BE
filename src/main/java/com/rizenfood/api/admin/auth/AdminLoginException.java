package com.rizenfood.api.admin.auth;

/**
 * 로그인 실패. 메시지는 사용자에게 그대로 보여준다.
 */
public class AdminLoginException extends RuntimeException {

    public AdminLoginException(String message) {
        super(message);
    }
}
