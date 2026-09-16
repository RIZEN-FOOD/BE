package com.rizenfood.api.feature;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MainFeatureRepository extends JpaRepository<MainFeature, Long> {

    List<MainFeature> findByVisibleTrueOrderBySortOrderAscIdAsc();

    List<MainFeature> findAllByOrderBySortOrderAscIdAsc();

    /** 새 칸을 맨 뒤에 붙이기 위한 값. 하나도 없으면 0. */
    @Query("select coalesce(max(f.sortOrder), 0) from MainFeature f")
    int findMaxSortOrder();
}
