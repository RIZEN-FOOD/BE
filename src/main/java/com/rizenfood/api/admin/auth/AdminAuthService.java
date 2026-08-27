package com.rizenfood.api.admin.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.admin.AdminUser;
import com.rizenfood.api.admin.AdminUserRepository;

/**
 * 관리자 로그인 처리.
 *
 * 두 가지를 지킨다.
 *  1. 실패 사유를 구분해서 알려주지 않는다.
 *     "없는 아이디"와 "틀린 비밀번호"를 구분해 주면 어떤 아이디가 존재하는지 알려주는 셈이다.
 *  2. 5회 실패하면 10분 잠근다 (기획서 §6.2).
 *
 * 실패·성공 기록은 AdminLoginAttemptService 가 별도 트랜잭션으로 맡는다.
 * 이 메서드는 실패를 예외로 알리는데, 같은 트랜잭션에서 기록하면
 * 그 예외가 기록까지 롤백시켜 잠금이 영영 걸리지 않는다.
 */
@Service
public class AdminAuthService {

    /**
     * 존재하지 않는 계정에도 대조 시간을 쓰기 위한 더미 해시.
     * 응답 시간 차이로 계정 존재 여부를 알아내는 것을 막는다.
     * (실제 BCrypt 해시 형식이어야 matches 가 계산을 수행한다)
     */
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa9rB4gVBoT0aXaJqDLmT0dCkG0K8QOe";

    private final AdminUserRepository repository;
    private final AdminLoginAttemptService attemptService;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(AdminUserRepository repository,
                            AdminLoginAttemptService attemptService,
                            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.attemptService = attemptService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AdminUser authenticate(String username, String rawPassword) {
        AdminUser admin = repository.findByUsername(username).orElse(null);

        if (admin == null) {
            // 계정이 없어도 한 번 대조해서 응답 시간을 맞춘다.
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new AdminLoginException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!admin.isEnabled()) {
            throw new AdminLoginException("사용할 수 없는 계정입니다. 관리자에게 문의해 주세요.");
        }

        if (admin.isLocked()) {
            throw new AdminLoginException(
                    "로그인 시도가 너무 많습니다. " + admin.lockRemainingMinutes() + "분 후에 다시 시도해 주세요.");
        }

        if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            AdminLoginAttemptService.FailureResult result = attemptService.recordFailure(admin.getId());

            if (result.locked()) {
                throw new AdminLoginException(
                        "로그인 시도가 너무 많습니다. " + AdminUser.LOCK_MINUTES + "분 후에 다시 시도해 주세요.");
            }
            throw new AdminLoginException(
                    "아이디 또는 비밀번호가 올바르지 않습니다. "
                            + result.attemptsLeft() + "회 더 틀리면 계정이 잠깁니다.");
        }

        attemptService.recordSuccess(admin.getId());
        return admin;
    }
}
