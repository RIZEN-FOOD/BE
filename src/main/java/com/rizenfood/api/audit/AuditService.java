package com.rizenfood.api.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 행위를 기록한다.
 *
 * 기록 실패가 본 작업을 되돌리면 안 된다.
 * 예를 들어 상품 저장은 성공했는데 로그 저장이 실패했다고 상품 저장까지 롤백되면
 * 사용자는 이유를 알 수 없다. 그래서 별도 트랜잭션으로 분리한다.
 */
@Service
public class AuditService {

    private final AdminAuditLogRepository repository;

    public AuditService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long adminId, String adminName, String action,
                       String targetType, String targetId, String summary,
                       HttpServletRequest request) {
        repository.save(new AdminAuditLog(
                adminId, adminName, action, targetType, targetId, summary,
                clientIp(request), request == null ? null : request.getHeader("User-Agent")));
    }

    /**
     * 실제 클라이언트 IP.
     *
     * nginx·CloudFront 뒤에 있으면 getRemoteAddr 은 프록시 주소를 준다.
     * X-Forwarded-For 의 첫 값이 원 클라이언트다.
     *
     * ⚠️ 이 헤더는 클라이언트가 위조할 수 있다. 신뢰할 수 있는 프록시가
     *    헤더를 덮어쓰도록 배포 단계에서 설정해야 이 값이 의미를 갖는다.
     */
    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first.length() > 64 ? first.substring(0, 64) : first;
            }
        }
        return request.getRemoteAddr();
    }
}
