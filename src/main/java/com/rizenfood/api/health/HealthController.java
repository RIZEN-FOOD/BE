package com.rizenfood.api.health;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 생존 확인용 엔드포인트.
 *
 * 로드밸런서·UptimeRobot 이 호출하므로 인증 없이 열어둔다(SecurityConfig 참조).
 * 내부 구조를 노출하지 않도록 버전·DB 정보 같은 건 담지 않는다.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "time", OffsetDateTime.now().toString()
        ));
    }
}
