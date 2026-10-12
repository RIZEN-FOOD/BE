package com.rizenfood.api.order;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderClaimRepository extends JpaRepository<OrderClaim, Long> {

    List<OrderClaim> findByOrderIdOrderByRequestedAtDesc(Long orderId);

    /** 이 주문에 아직 끝나지 않은 요청이 있는지 (접수·승인 상태). */
    boolean existsByOrderIdAndStatusIn(Long orderId, java.util.Collection<String> statuses);

    Page<OrderClaim> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<OrderClaim> findByStatusOrderByRequestedAtDesc(String status, Pageable pageable);

    /** 기간 안에 환불까지 끝난 취소·반품 (매출에서 뺀다). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM OrderClaim c
             WHERE c.status = 'COMPLETED'
               AND c.type IN ('CANCEL', 'RETURN')
               AND c.refundAmount > 0
               AND c.processedAt >= :from AND c.processedAt < :to
             ORDER BY c.processedAt
            """)
    List<OrderClaim> findRefundsBetween(@org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                        @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}
