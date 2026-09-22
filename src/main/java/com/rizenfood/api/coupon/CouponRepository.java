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

    /*
     * ★ 위 두 개를 부른 «뒤에» 엔티티를 고치지 마라. (2026-09-22)
     *
     * clearAutomatically = true 는 이 UPDATE 가 끝나면 영속성 컨텍스트를 비운다.
     * 그래야 같은 트랜잭션에서 쿠폰을 다시 읽을 때 낡은 usedCount 를 보지 않는다.
     * 대신 그 순간 들고 있던 엔티티가 전부 detached 가 되므로, 그 «뒤에» 고친 값은
     * flush 되지 않고 조용히 사라진다.
     *
     * 실제로 이걸로 한 번 당했다 — 주문 취소에서 재고와 쿠폰은 돌아왔는데
     * 주문 상태만 «결제 대기» 로 남았다. 손님은 취소한 주문을 결제 대기로 보고,
     * 그 주문이 1인 한도를 계속 차지해 같은 코드를 다시 못 썼다.
     *
     * 규칙: 주문·클레임 상태를 먼저 바꾸고, 쿠폰 반환을 «맨 마지막에» 부른다.
     * flushAutomatically = true 라 앞에서 고친 값은 이 UPDATE 직전에 DB 에 닿는다.
     */

    /** 집계 화면에서 이름을 붙이려고 한 번에 읽는다. */
    List<Coupon> findByIdIn(List<Long> ids);
}
