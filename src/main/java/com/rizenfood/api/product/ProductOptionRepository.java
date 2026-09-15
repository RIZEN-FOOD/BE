package com.rizenfood.api.product;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    /**
     * 원자적 재고 차감. 읽고-나서-쓰기를 하지 않는다 (CLAUDE.md 규칙 5).
     * 재고가 충분할 때만 1행이 갱신된다. 0 이면 재고 부족이다.
     */
    @Modifying
    @Query("update ProductOption o set o.stock = o.stock - :qty "
         + "where o.id = :id and o.stock >= :qty")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** 차감 직후 잔량. 스칼라 조회라 DB 값을 읽는다. */
    @Query("select o.stock from ProductOption o where o.id = :id")
    Integer currentStock(@Param("id") Long id);

    @Modifying
    @Query("update ProductOption o set o.stock = o.stock + :qty where o.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}
