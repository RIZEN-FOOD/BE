package com.rizenfood.api.admin.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.admin.AdminUser;
import com.rizenfood.api.admin.AdminUserRepository;

/**
 * 로그인 시도 결과를 기록한다.
 *
 * ★ 별도 트랜잭션이어야 한다.
 *   로그인 실패는 예외로 알린다. 그런데 실패 기록을 같은 트랜잭션에서 저장하면
 *   그 예외가 트랜잭션을 롤백시키면서 방금 올린 실패 횟수까지 되돌린다.
 *   그러면 몇 번을 틀려도 카운트가 0 이라 잠금이 영영 걸리지 않는다.
 *
 *   스프링 프록시는 같은 객체 안에서의 호출에는 걸리지 않으므로,
 *   REQUIRES_NEW 를 살리려면 이렇게 별도 빈으로 떼어놔야 한다.
 */
@Service
public class AdminLoginAttemptService {

    private final AdminUserRepository repository;

    public AdminLoginAttemptService(AdminUserRepository repository) {
        this.repository = repository;
    }

    /**
     * @return 기록 후의 상태. 이번 실패로 잠겼는지, 몇 번 남았는지 알려준다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureResult recordFailure(Long adminId) {
        AdminUser admin = repository.findById(adminId).orElseThrow();
        admin.recordFailure();
        repository.saveAndFlush(admin);
        return new FailureResult(
                admin.isLocked(),
                Math.max(0, AdminUser.MAX_FAILED_ATTEMPTS - admin.getFailedCount()),
                admin.lockRemainingMinutes());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long adminId) {
        AdminUser admin = repository.findById(adminId).orElseThrow();
        admin.recordSuccess();
        repository.saveAndFlush(admin);
    }

    public record FailureResult(boolean locked, int attemptsLeft, long lockMinutes) {
    }
}
