package com.rizenfood.api.image;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 업로드 실패를 사용자가 읽을 수 있는 문장으로 돌려준다.
 * 관리자 화면을 쓰는 사람은 개발자가 아니다.
 */
@RestControllerAdvice
public class ImageExceptionHandler {

    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ImageValidationException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_IMAGE", "message", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "FILE_TOO_LARGE", "message", "파일이 너무 큽니다. 10MB 이하로 올려 주세요."));
    }
}
