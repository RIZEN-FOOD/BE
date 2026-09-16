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
}
