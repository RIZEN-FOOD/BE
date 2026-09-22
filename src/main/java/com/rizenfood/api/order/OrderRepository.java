package com.rizenfood.api.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByOrderNoAndMemberId(String orderNo, Long memberId);

    boolean existsByOrderNo(String orderNo);

    Page<Order> findByMemberIdOrderByOrderedAtDesc(Long memberId, Pageable pageable);

    /** 회원별 주문 건수 (관리자 회원 상세의 주문 요약). */
    long countByMemberId(Long memberId);

    // ── 관리자 ──
    Page<Order> findAllByOrderByOrderedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByOrderedAtDesc(String status, Pageable pageable);

    // ── 결제 ──
    /** 방치된 미결제 주문 정리용 (결제창 이탈 등). */
    @EntityGraph(attributePaths = "items")
    java.util.List<Order> findByStatusAndOrderedAtBefore(String status, java.time.Instant before);

    /** 출고용 엑셀. 한 번에 최대 1,000건, 오래된 주문부터. */
    java.util.List<Order> findTop1000ByStatusInOrderByOrderedAtAsc(java.util.Collection<String> statuses);

    // ── 할인코드 ──────────────────────────────────────────────

    /**
     * 이 사람이 이 코드를 이미 몇 번 썼나 (회원 기준).
     * 취소·환불된 주문은 세지 않는다 — 그때 사용 한 장을 되돌려주기 때문이다.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(o) FROM Order o
             WHERE o.couponId = :couponId
               AND o.memberId = :memberId
               AND o.status NOT IN ('CANCELLED', 'REFUNDED')
            """)
    long countUsedByMember(@org.springframework.data.repository.query.Param("couponId") Long couponId,
                           @org.springframework.data.repository.query.Param("memberId") Long memberId);

    /**
     * 이 번호가 이 코드를 이미 몇 번 썼나 (비회원까지 포함).
     * 암호문은 매번 달라 검색이 안 되므로 조회용 해시로 찾는다 (PhoneHasher).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(o) FROM Order o
             WHERE o.couponId = :couponId
               AND o.ordererPhoneHash = :phoneHash
               AND o.status NOT IN ('CANCELLED', 'REFUNDED')
            """)
    long countUsedByPhone(@org.springframework.data.repository.query.Param("couponId") Long couponId,
                          @org.springframework.data.repository.query.Param("phoneHash") String phoneHash);

    /** 코드별 사용 건수와 매출 (관리자 집계). 취소·환불 건은 뺀다. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT o.couponId, COUNT(o), COALESCE(SUM(o.totalAmount), 0), COALESCE(SUM(o.discountAmount), 0)
              FROM Order o
             WHERE o.couponId IS NOT NULL
               AND o.status NOT IN ('CANCELLED', 'REFUNDED')
             GROUP BY o.couponId
            """)
    java.util.List<Object[]> summarizeByCoupon();
}
