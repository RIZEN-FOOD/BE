package com.rizenfood.api.order;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderClaimRepository extends JpaRepository<OrderClaim, Long> {

    List<OrderClaim> findByOrderIdOrderByRequestedAtDesc(Long orderId);

    Page<OrderClaim> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<OrderClaim> findByStatusOrderByRequestedAtDesc(String status, Pageable pageable);
}
