package com.rizenfood.api.popup;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    /** 지금 떠야 하는 팝업 (노출 기간까지 SQL 에서 거른다). */
    @Query("""
            select p from Popup p
            where p.visible = true
              and (p.alwaysOn = true
                   or (p.startAt <= :now and p.endAt > :now))
            order by p.sortOrder asc, p.id asc
            """)
    List<Popup> findActive(@Param("now") Instant now);

    List<Popup> findAllByOrderBySortOrderAscIdAsc();

    @Query("select coalesce(max(p.sortOrder), 0) from Popup p")
    int findMaxSortOrder();
}
