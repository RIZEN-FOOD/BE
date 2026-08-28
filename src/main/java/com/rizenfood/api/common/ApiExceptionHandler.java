package com.rizenfood.api.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.rizenfood.api.product.ProductService;

/**
 * 공통 오류 응답.
 *
 * 관리자 화면을 쓰는 사람은 개발자가 아니다.
 * 무엇이 잘못됐고 어떻게 하면 되는지가 문장 안에 있어야 한다.
 *
 * 예상하지 못한 오류는 내부 사정을 밖으로 흘리지 않는다.
 * 스택트레이스나 SQL 이 그대로 나가면 공격자에게 구조를 알려주는 셈이다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(ProductService.DuplicateSlugException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSlug(
            ProductService.DuplicateSlugException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "DUPLICATE_SLUG", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
    }

    /**
     * 입력값 검증 실패.
     * 어느 칸이 왜 잘못됐는지 필드별로 알려줘야 관리자 화면이 그 칸에 표시할 수 있다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(), err.getDefaultMessage()));

        String first = fields.values().stream().findFirst().orElse("입력값을 확인해 주세요.");

        return ResponseEntity.badRequest().body(Map.of(
                "error", "VALIDATION_FAILED",
                "message", first,
                "fields", fields));
    }

    /**
     * 본문을 읽지 못했을 때. 형식이 깨진 JSON, 인코딩 불일치 등.
     * 클라이언트 잘못이므로 400 이다. 500 으로 주면 서버 장애로 오인된다.
     *
     * 파서 메시지는 그대로 내보내지 않는다. 내부 구조를 흘릴 수 있다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽지 못했다: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "MALFORMED_REQUEST",
                "message", "요청 형식이 올바르지 않습니다. 본문이 UTF-8 JSON 인지 확인해 주세요."));
    }

    /**
     * 권한 부족 (@PreAuthorize 거부).
     * 인증은 됐지만 권한이 없는 경우다. 403 으로 준다.
     * 이걸 잡지 않으면 아래 Exception 핸들러가 500 으로 처리해 버린다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "FORBIDDEN", "message", "이 작업을 수행할 권한이 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        // 자세한 내용은 서버 로그에만 남긴다.
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR",
                        "message", "처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
