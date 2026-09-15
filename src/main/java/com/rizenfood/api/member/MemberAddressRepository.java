package com.rizenfood.api.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    /** 기본 배송지 먼저, 그다음 최신순. */
    List<MemberAddress> findByMemberIdOrderByIsDefaultDescCreatedAtDesc(Long memberId);

    /** 남의 주소를 만지지 못하도록 항상 memberId 로 함께 좁힌다. */
    Optional<MemberAddress> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberId(Long memberId);

    boolean existsByMemberIdAndIsDefaultTrue(Long memberId);

    /** 기본 배송지를 새로 지정하기 전에 기존 기본을 모두 내린다. */
    @Modifying
    @Query("update MemberAddress a set a.isDefault = false where a.memberId = :memberId and a.isDefault = true")
    void clearDefault(@Param("memberId") Long memberId);
}
