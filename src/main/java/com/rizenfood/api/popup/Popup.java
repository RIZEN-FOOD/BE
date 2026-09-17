package com.rizenfood.api.popup;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 메인 화면 팝업.
 *
 * 이미지 한 장 + (선택) 이동 버튼. 노출 기간이 지나면 자동으로 내려간다.
 * always_on 이 false 면 start_at ~ end_at 구간에만 보인다 (배너와 같은 규칙).
 */
@Entity
@Table(name = "popup")
public class Popup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    @Column(name = "show_hide_today", nullable = false)
    private boolean showHideToday = true;

    @Column(name = "show_link_button", nullable = false)
    private boolean showLinkButton = false;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "always_on", nullable = false)
    private boolean alwaysOn = true;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Popup() {
    }

    public Popup(String title, String imageKey) {
        this.title = title;
        this.imageKey = imageKey;
    }

    /** 지금 노출되어야 하는가. SQL 로 거른 뒤 응답 직전에 한 번 더 본다. */
    public boolean isActiveNow() {
        if (!visible) {
            return false;
        }
        if (alwaysOn) {
            return true;
        }
        Instant now = Instant.now();
        return startAt != null && endAt != null && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageKey() { return imageKey; }
    public boolean isShowHideToday() { return showHideToday; }
    public boolean isShowLinkButton() { return showLinkButton; }
    public String getLinkUrl() { return linkUrl; }
    public boolean isAlwaysOn() { return alwaysOn; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String v) { this.title = v; }
    public void setImageKey(String v) { this.imageKey = v; }
    public void setShowHideToday(boolean v) { this.showHideToday = v; }
    public void setShowLinkButton(boolean v) { this.showLinkButton = v; }
    public void setLinkUrl(String v) { this.linkUrl = v; }
    public void setAlwaysOn(boolean v) { this.alwaysOn = v; }
    public void setStartAt(Instant v) { this.startAt = v; }
    public void setEndAt(Instant v) { this.endAt = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setVisible(boolean v) { this.visible = v; }
}
