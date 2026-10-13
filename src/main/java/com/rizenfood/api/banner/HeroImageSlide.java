package com.rizenfood.api.banner;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 메인 히어로 — 이미지 한 장 슬라이드 (V41, 2026-10-13).
 *
 * 사진 한 장이 히어로 화면 전체를 덮는다. 누끼 슬라이드(상품의 hero_* 필드)와
 * 같은 순서 숫자로 섞여 메인 캐러셀에서 돈다.
 *
 * ★ 버튼은 문구와 이동 주소가 둘 다 있을 때만 나온다.
 * ★ 이동 주소는 사이트 안 주소(/…) 또는 http(s) 만 받는다 — javascript: 같은 주소가 링크로 나가면 위험하다.
 */
@Entity
@Table(name = "hero_image_slide")
public class HeroImageSlide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_pc_key", nullable = false, length = 500)
    private String imagePcKey;

    /** 비어 있으면 휴대폰에서도 PC 사진을 쓴다(가운데 기준으로 잘림). */
    @Column(name = "image_mobile_key", length = 500)
    private String imageMobileKey;

    @Column(name = "alt_text", nullable = false, length = 300)
    private String altText;

    @Column(name = "button_label", length = 40)
    private String buttonLabel;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "open_new_tab", nullable = false)
    private boolean openNewTab = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected HeroImageSlide() {
    }

    public HeroImageSlide(String imagePcKey, String altText) {
        this.imagePcKey = imagePcKey;
        this.altText = altText;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getImagePcKey() { return imagePcKey; }
    public String getImageMobileKey() { return imageMobileKey; }
    public String getAltText() { return altText; }
    public String getButtonLabel() { return buttonLabel; }
    public String getLinkUrl() { return linkUrl; }
    public boolean isOpenNewTab() { return openNewTab; }
    public int getSortOrder() { return sortOrder; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setImagePcKey(String v) { this.imagePcKey = v; }
    public void setImageMobileKey(String v) { this.imageMobileKey = v; }
    public void setAltText(String v) { this.altText = v; }
    public void setButtonLabel(String v) { this.buttonLabel = v; }
    public void setLinkUrl(String v) { this.linkUrl = v; }
    public void setOpenNewTab(boolean v) { this.openNewTab = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setEnabled(boolean v) { this.enabled = v; }
}
