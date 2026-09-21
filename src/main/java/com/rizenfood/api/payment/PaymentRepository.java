package com.rizenfood.api.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /** 주문번호로 PG 거래키만 읽는다. 결제 어댑터가 승인·취소에 쓴다. */
    @Query("""
            SELECT p.pgTid FROM Payment p
            WHERE p.orderId = (SELECT o.id FROM Order o WHERE o.orderNo = :orderNo)
            """)
    String findTidByOrderNo(@Param("orderNo") String orderNo);
}
