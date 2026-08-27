package com.rizenfood.api.banner;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /**
     * 특정 위치의 공개 배너.
     * 노출 기간(always_on 이거나 now 가 구간 안)까지 SQL 에서 거른다.
     */
    @Query("""
            select b from Banner b
            where b.visible = true
              and b.position = :position
              and (b.alwaysOn = true
                   or (b.startAt <= :now and b.endAt > :now))
            order by b.sortOrder asc, b.id asc
            """)
    List<Banner> findActive(@Param("position") String position, @Param("now") Instant now);

    /** 관리자 목록. 위치별로 묶어 보기 위해 정렬한다. */
    List<Banner> findAllByOrderByPositionAscSortOrderAscIdAsc();

    @Query("select coalesce(max(b.sortOrder), 0) from Banner b where b.position = :position")
    int findMaxSortOrder(@Param("position") String position);
}
