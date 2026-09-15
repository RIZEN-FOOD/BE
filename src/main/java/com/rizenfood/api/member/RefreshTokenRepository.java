package com.rizenfood.api.member;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 로그아웃·탈퇴 시 회원의 모든 리프레시 토큰을 무효화한다. */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.memberId = :memberId and t.revokedAt is null")
    void revokeAllByMember(@Param("memberId") Long memberId, @Param("now") Instant now);

    /** 만료된 토큰 정리 (배치용) */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
