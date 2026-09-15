package com.rizenfood.api.shipping;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingPolicyRepository extends JpaRepository<ShippingPolicy, Long> {

    /** 활성 정책. 스키마상 활성은 하나뿐이지만 안전하게 첫 건을 집는다. */
    Optional<ShippingPolicy> findFirstByVisibleTrueOrderByIdAsc();
}
