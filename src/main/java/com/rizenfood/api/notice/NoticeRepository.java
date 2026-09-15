package com.rizenfood.api.notice;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 공개 목록. 발행 시각이 지난 것만.
     * 고정 공지를 맨 위에, 그다음 발행일 최신순.
     */
    @Query("""
            select n from Notice n
            where n.visible = true
              and n.publishedAt is not null
              and n.publishedAt <= :now
            order by n.pinned desc, n.publishedAt desc
            """)
    Page<Notice> findPublic(@Param("now") Instant now, Pageable pageable);

    /** 공개 목록 + 제목 검색 */
    @Query("""
            select n from Notice n
            where n.visible = true
              and n.publishedAt is not null
              and n.publishedAt <= :now
              and lower(n.title) like lower(concat('%', :keyword, '%'))
            order by n.pinned desc, n.publishedAt desc
            """)
    Page<Notice> searchPublic(@Param("now") Instant now, @Param("keyword") String keyword, Pageable pageable);

    /** 공개 상세. 미발행·숨김 공지는 없는 것처럼 다룬다. */
    @Query("""
            select n from Notice n
            where n.id = :id
              and n.visible = true
              and n.publishedAt is not null
              and n.publishedAt <= :now
            """)
    Optional<Notice> findPublicById(@Param("id") Long id, @Param("now") Instant now);

    /**
     * 조회수 1 증가. 엔티티를 읽어 올리지 않고 UPDATE 한 번으로 처리한다.
     * 동시에 여러 명이 봐도 경합 없이 정확히 센다.
     */
    @Modifying
    @Query("update Notice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    void increaseViewCount(@Param("id") Long id);
}
