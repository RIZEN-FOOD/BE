package com.rizenfood.api.admin.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdminAuthExceptionHandler {

    /**
     * 401 로 준다. 사유(없는 계정 / 틀린 비밀번호 / 잠금)는 메시지로만 구분되고,
     * 계정 존재 여부를 알려주는 문구는 쓰지 않는다.
     */
    @ExceptionHandler(AdminLoginException.class)
    public ResponseEntity<Map<String, String>> handleLoginFailure(AdminLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "LOGIN_FAILED", "message", e.getMessage()));
    }
}
