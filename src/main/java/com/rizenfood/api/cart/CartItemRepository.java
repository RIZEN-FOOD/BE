package com.rizenfood.api.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** 장바구니 조회 시 상품·옵션을 함께 끌어와 N+1 을 막는다. */
    @EntityGraph(attributePaths = {"product", "option"})
    List<CartItem> findByCartIdOrderByAddedAtAsc(Long cartId);

    Optional<CartItem> findByCartIdAndId(Long cartId, Long id);
}
