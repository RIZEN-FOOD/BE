package com.rizenfood.api.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);

    /** 발송한 지 오래된 배송 건. 배송완료 자동 처리에 쓴다. */
    List<Delivery> findTop500ByStatusAndShippedAtBefore(String status, Instant before);
}
