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
}
