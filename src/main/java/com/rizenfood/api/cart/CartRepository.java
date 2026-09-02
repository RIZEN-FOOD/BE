package com.rizenfood.api.cart;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);

    Optional<Cart> findByGuestToken(String guestToken);

    /** 오래된 게스트 장바구니 정리 배치용. 지금은 스케줄러가 없지만 미리 둔다. */
    @Query("delete from Cart c where c.guestToken is not null and c.updatedAt < :before")
    int deleteStaleGuestCarts(@Param("before") Instant before);
}
