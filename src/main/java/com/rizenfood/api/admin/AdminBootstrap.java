package com.rizenfood.api.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정이 하나도 없을 때 첫 계정을 만든다.
 *
 * 비밀번호를 마이그레이션 SQL 에 박지 않는 이유는, 그 값이 저장소에 영원히 남기 때문이다.
 * 대신 환경변수로 받아 첫 기동에만 쓴다.
 *
 * 계정이 이미 있으면 아무것도 하지 않는다. 기존 비밀번호를 덮어쓰지 않는다.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrap(AdminUserRepository repository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.bootstrap.username:}") String username,
                          @Value("${app.admin.bootstrap.password:}") String password,
                          @Value("${app.admin.bootstrap.display-name:관리자}") String displayName) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            return;
        }
        if (repository.count() > 0) {
            return;
        }
        if (password.length() < 8) {
            log.warn("초기 관리자 비밀번호가 8자 미만이라 계정을 만들지 않았다. ADMIN_BOOTSTRAP_PASSWORD 를 확인하라.");
            return;
        }

        repository.save(new AdminUser(
                username, passwordEncoder.encode(password), displayName, "SUPER_ADMIN"));

        // 비밀번호는 절대 로그에 남기지 않는다.
        log.info("초기 관리자 계정을 만들었다: {}. 첫 로그인 후 비밀번호를 바꿔야 한다.", username);
    }
}
