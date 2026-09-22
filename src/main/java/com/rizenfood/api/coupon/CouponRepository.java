package com.rizenfood.api.coupon;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    Page<Coupon> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 남은 수량을 원자적으로 깎는다. 재고와 같은 방식이다 — 읽고-나서-쓰기를 하지 않는다.
     * 한도가 없으면(total_quantity IS NULL) 언제나 성공한다.
     *
     * @return 1 이면 확보 성공, 0 이면 이미 소진
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Coupon c
               SET c.usedCount = c.usedCount + 1
             WHERE c.id = :id
               AND (c.totalQuantity IS NULL OR c.usedCount < c.totalQuantity)
            """)
    int take(@Param("id") Long id);

    /** 주문이 취소되면 한 장을 되돌린다. 0 아래로는 내려가지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount - 1 WHERE c.id = :id AND c.usedCount > 0")
    int giveBack(@Param("id") Long id);

    /** 집계 화면에서 이름을 붙이려고 한 번에 읽는다. */
    List<Coupon> findByIdIn(List<Long> ids);
}
