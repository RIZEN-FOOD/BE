package com.rizenfood.api.admin;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 사용 중인 계정 수 (마지막 최고관리자 보호용). */
    long countByRoleAndEnabledTrue(String role);
}
