package com.rizenfood.api.wishlist;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    @EntityGraph(attributePaths = "product")
    List<WishlistItem> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    void deleteByMemberIdAndProductId(Long memberId, Long productId);

    List<WishlistItem> findByMemberIdAndProductId(Long memberId, Long productId);
}
