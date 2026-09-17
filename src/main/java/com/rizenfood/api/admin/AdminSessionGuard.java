package com.rizenfood.api.admin;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.security.JwtTokenProvider;

/**
 * 관리자 로그인 토큰이 아직 유효한지 DB 와 대조한다.
 *
 * 토큰은 서명만 확인하면 만료(4시간)까지 쓸 수 있다. 그러면 비밀번호를 바꾸거나
 * 계정을 중지해도 이미 나간 토큰은 계속 살아 있다. 그래서 요청마다
 * "계정이 사용 중인가, 세션 번호가 같은가"를 본다. 관리자 요청은 많지 않아 부담이 작다.
 */
@Component
public class AdminSessionGuard {

    private final AdminUserRepository repository;

    public AdminSessionGuard(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isCurrent(JwtTokenProvider.AuthenticatedAdmin admin) {
        return repository.findById(admin.id())
                .map(a -> a.isEnabled()
                        && a.getTokenVersion() == admin.tokenVersion()
                        && a.getRole().equals(admin.role()))
                .orElse(false);
    }
}
