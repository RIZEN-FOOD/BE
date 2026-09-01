package com.rizenfood.api.review;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 사이트 전체 공개 후기 (모아보기 페이지) */
    @EntityGraph(attributePaths = {"product", "images"})
    Page<Review> findByVisibleTrue(Pageable pageable);

    /** 특정 상품의 공개 후기 */
    @EntityGraph(attributePaths = {"product", "images"})
    Page<Review> findByProductIdAndVisibleTrue(Long productId, Pageable pageable);

    /** 내 후기 (탈퇴 전이라 member_id 로 조회 가능한 동안만) */
    @EntityGraph(attributePaths = {"product", "images"})
    Page<Review> findByMemberId(Long memberId, Pageable pageable);

    /** 관리자 대기함: 아직 공개 처리되지 않은 것 */
    @EntityGraph(attributePaths = {"product", "images"})
    Page<Review> findByVisibleFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"product", "images"})
    Page<Review> findAll(Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId and r.visible = true")
    Optional<Double> averageRating(Long productId);

    long countByProductIdAndVisibleTrue(Long productId);

    boolean existsByIdAndMemberId(Long id, Long memberId);
}
