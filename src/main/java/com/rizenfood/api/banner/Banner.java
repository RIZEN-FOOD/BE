package com.rizenfood.api.banner;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 배너.
 *
 * ★ PC 와 모바일 이미지를 각각 받는다. 하나로 쓰면 어느 한쪽이 반드시 깨진다 (기획서 §7.3).
 * ★ 대체 텍스트(alt_text)는 접근성 필수라 비워둘 수 없다.
 *
 * 노출 기간이 지나면 자동으로 내려간다. 관리자가 손대지 않아도 된다.
 * always_on 이 false 면 start_at ~ end_at 구간에만 보인다.
 */
@Entity
@Table(name = "banner")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 관리용 제목. 화면에는 나오지 않는다. */
    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "image_pc_key", nullable = false, length = 500)
    private String imagePcKey;

    @Column(name = "image_mobile_key", nullable = false, length = 500)
    private String imageMobileKey;

    @Column(name = "alt_text", nullable = false, length = 300)
    private String altText;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    /** MAIN_TOP | MAIN_MID | PRODUCT_TOP */
    @Column(nullable = false, length = 30)
    private String position;

    @Column(name = "open_new_tab", nullable = false)
    private boolean openNewTab = false;

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

    protected Banner() {
    }

    public Banner(String title, String imagePcKey, String imageMobileKey, String altText, String position) {
        this.title = title;
        this.imagePcKey = imagePcKey;
        this.imageMobileKey = imageMobileKey;
        this.altText = altText;
        this.position = position;
    }

    /**
     * 지금 노출되어야 하는가.
     * 공개 조회는 SQL 에서 한 번 거르지만, 응답 직전에도 한 번 더 확인해
     * 캐시나 시간차로 만료된 배너가 나가지 않게 한다.
     */
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
    public String getImagePcKey() { return imagePcKey; }
    public String getImageMobileKey() { return imageMobileKey; }
    public String getAltText() { return altText; }
    public String getLinkUrl() { return linkUrl; }
    public String getPosition() { return position; }
    public boolean isOpenNewTab() { return openNewTab; }
    public boolean isAlwaysOn() { return alwaysOn; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }

    public void setTitle(String v) { this.title = v; }
    public void setImagePcKey(String v) { this.imagePcKey = v; }
    public void setImageMobileKey(String v) { this.imageMobileKey = v; }
    public void setAltText(String v) { this.altText = v; }
    public void setLinkUrl(String v) { this.linkUrl = v; }
    public void setPosition(String v) { this.position = v; }
    public void setOpenNewTab(boolean v) { this.openNewTab = v; }
    public void setAlwaysOn(boolean v) { this.alwaysOn = v; }
    public void setStartAt(Instant v) { this.startAt = v; }
    public void setEndAt(Instant v) { this.endAt = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setVisible(boolean v) { this.visible = v; }
}
