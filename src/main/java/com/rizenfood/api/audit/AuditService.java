package com.rizenfood.api.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.security.ClientIpResolver;

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
    private final ClientIpResolver ipResolver;

    public AuditService(AdminAuditLogRepository repository, ClientIpResolver ipResolver) {
        this.repository = repository;
        this.ipResolver = ipResolver;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long adminId, String adminName, String action,
                       String targetType, String targetId, String summary,
                       HttpServletRequest request) {
        // 접속자 IP 는 위조 가능한 X-Forwarded-For 가 아니라 ClientIpResolver 가 정한다.
        repository.save(new AdminAuditLog(
                adminId, adminName, action, targetType, targetId, summary,
                ipResolver.resolve(request), request == null ? null : request.getHeader("User-Agent")));
    }
}
