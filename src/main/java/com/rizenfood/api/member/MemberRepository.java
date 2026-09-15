package com.rizenfood.api.member;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 로컬(이메일) 회원 조회. 소셜 회원과 이메일이 겹칠 수 있어 provider 로 좁힌다. */
    Optional<Member> findByEmailAndProvider(String email, String provider);

    boolean existsByEmailAndProvider(String email, String provider);

    /**
     * 관리자 회원 검색.
     *
     * q 는 이메일·이름 부분 일치(대소문자 무시). status 는 정확 일치.
     * 둘 다 빈 문자열("")이면 조건을 걸지 않고 전체를 최신 가입순으로 준다.
     * 휴대폰은 암호문으로 저장돼 있어 검색 대상에 넣지 않는다.
     *
     * ★ null 이 아니라 빈 문자열로 받는다. PostgreSQL 은 null 바인드 파라미터를
     *   문자열 함수(lower/concat) 안에서 타입 추론하지 못해 bytea 로 잡고 실패한다.
     *   서비스에서 빈 값을 "" 로 정규화해 넘긴다.
     */
    @Query("""
            select m from Member m
            where (:q = '' or lower(m.email) like lower(concat('%', :q, '%'))
                           or lower(m.name)  like lower(concat('%', :q, '%')))
              and (:status = '' or m.status = :status)
            """)
    Page<Member> searchForAdmin(@Param("q") String q,
                                @Param("status") String status,
                                Pageable pageable);
}
