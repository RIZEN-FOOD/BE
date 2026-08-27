package com.rizenfood.api.notice;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 공지사항.
 *
 * published_at 에 미래 시각을 넣으면 그때까지 공개되지 않는다 (발행 예약).
 * is_pinned 는 목록 맨 위에 고정한다.
 * body_html 은 에디터 입력이라 저장 시 HTML 살균을 거친다.
 */
@Entity
@Table(name = "notice")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NOTICE | EVENT | INFO */
    @Column(nullable = false, length = 20)
    private String category = "NOTICE";

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    /** 이 시각 이후에만 공개된다. null 이면 미발행(임시저장). */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Notice() {
    }

    public Notice(String category, String title, String bodyHtml) {
        this.category = category;
        this.title = title;
        this.bodyHtml = bodyHtml;
    }

    /** 지금 공개 상태인가. 예약 발행 시각이 지났고, 숨김이 아니어야 한다. */
    public boolean isPublicNow() {
        return visible && publishedAt != null && !publishedAt.isAfter(Instant.now());
    }

    public void increaseViewCount() {
        this.viewCount += 1;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getBodyHtml() { return bodyHtml; }
    public boolean isPinned() { return pinned; }
    public int getViewCount() { return viewCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public boolean isVisible() { return visible; }
    public Instant getCreatedAt() { return createdAt; }

    public void setCategory(String v) { this.category = v; }
    public void setTitle(String v) { this.title = v; }
    public void setBodyHtml(String v) { this.bodyHtml = v; }
    public void setPinned(boolean v) { this.pinned = v; }
    public void setPublishedAt(Instant v) { this.publishedAt = v; }
    public void setVisible(boolean v) { this.visible = v; }
}
