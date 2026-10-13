package com.rizenfood.api.banner;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HeroImageSlideRepository extends JpaRepository<HeroImageSlide, Long> {

    /** 메인에 나갈 슬라이드 (노출 켠 것, 순서대로). */
    List<HeroImageSlide> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /** 관리자 목록. */
    List<HeroImageSlide> findAllByOrderBySortOrderAscIdAsc();
}
