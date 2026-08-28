package com.rizenfood.api.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 로컬(이메일) 회원 조회. 소셜 회원과 이메일이 겹칠 수 있어 provider 로 좁힌다. */
    Optional<Member> findByEmailAndProvider(String email, String provider);

    boolean existsByEmailAndProvider(String email, String provider);
}
