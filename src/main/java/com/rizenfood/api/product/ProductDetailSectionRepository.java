package com.rizenfood.api.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductDetailSectionRepository extends JpaRepository<ProductDetailSection, Long> {

    List<ProductDetailSection> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    void deleteByProductId(Long productId);
}
