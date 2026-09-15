package com.rizenfood.api.member;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MemberAuthController.class)
public class MemberAuthExceptionHandler {

    /**
     * 회원 인증 실패. 401 로 준다.
     * 사유(없는 계정 / 틀린 비밀번호 / 잠금)는 메시지로만 구분하고,
     * 계정 존재 여부를 알려주는 문구는 쓰지 않는다.
     */
    @ExceptionHandler(MemberAuthException.class)
    public ResponseEntity<Map<String, String>> handle(MemberAuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "AUTH_FAILED", "message", e.getMessage()));
    }
}
