package com.rizenfood.api.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 원자적 재고 차감. 읽고-나서-쓰기를 하지 않는다 (CLAUDE.md 규칙 5).
     * 재고가 충분할 때만 1행이 갱신된다. 반환 0 이면 재고 부족이다.
     * (옵션이 있는 상품의 재고는 ProductOptionRepository 에서 차감한다)
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty "
         + "where p.id = :id and p.stock >= :qty")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** 차감 직후 잔량. 스칼라 조회라 영속성 컨텍스트의 낡은 엔티티가 아니라 DB 값을 읽는다. */
    @Query("select p.stock from Product p where p.id = :id")
    Integer currentStock(@Param("id") Long id);

    /** 취소·반품 시 재고 복원. */
    @Modifying
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** 공개 목록. visible=true 만 나간다. */
    Page<Product> findByVisibleTrue(Pageable pageable);

    /** 메인에 노출할 상품 */
    List<Product> findByVisibleTrueAndFeaturedTrueOrderBySortOrderAscIdAsc();

    /**
     * 공개 상세. 숨긴 상품은 없는 것처럼 다룬다.
     *
     * 여러 컬렉션을 EntityGraph 로 한 번에 fetch 하면 MultipleBagFetchException 이 난다.
     * 대신 기본 조회로 상품만 가져오고, 컬렉션은 @BatchSize 로 묶어 읽는다.
     * 서비스가 readOnly 트랜잭션 안에서 매핑하므로 지연 로딩이 그 안에서 해소된다.
     */
    Optional<Product> findBySlugAndVisibleTrue(String slug);

    /** 관리자 상세. 숨긴 상품도 보여야 한다. */
    Optional<Product> findWithDetailsById(Long id);

    boolean existsBySlug(String slug);

    @Query("select coalesce(max(p.sortOrder), 0) from Product p")
    int findMaxSortOrder();
}
