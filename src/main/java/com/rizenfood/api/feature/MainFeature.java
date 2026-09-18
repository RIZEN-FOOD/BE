package com.rizenfood.api.feature;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 메인 화면 FEATURES 한 칸.
 *
 * ★ 문구는 식품표시광고법을 지켜야 한다 (CLAUDE.md 규칙 1). 관리자 화면에 금지 표현을 안내한다.
 * ★ 본문을 비우고 autoNutritionBody 를 켜면 화면이 상품 영양성분으로 문장을 만든다
 *   — 수치를 코드나 DB 문장에 박지 않기 위해서다.
 */
@Entity
@Table(name = "main_feature")
public class MainFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body = "";

    @Column(name = "image_key", length = 500)
    private String imageKey;

    /** 모바일 전용 사진(세로). 비면 imageKey 를 쓴다. */
    @Column(name = "image_mobile_key", length = 500)
    private String imageMobileKey;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "auto_nutrition_body", nullable = false)
    private boolean autoNutritionBody = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MainFeature() {
    }

    public MainFeature(String title) {
        this.title = title;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }

    public String getBody() { return body; }
    public void setBody(String v) { this.body = v == null ? "" : v; }

    public String getImageKey() { return imageKey; }
    public void setImageKey(String v) { this.imageKey = (v == null || v.isBlank()) ? null : v; }

    public String getImageMobileKey() { return imageMobileKey; }
    public void setImageMobileKey(String v) { this.imageMobileKey = (v == null || v.isBlank()) ? null : v; }

    public String getAltText() { return altText; }
    public void setAltText(String v) { this.altText = (v == null || v.isBlank()) ? null : v; }

    public boolean isAutoNutritionBody() { return autoNutritionBody; }
    public void setAutoNutritionBody(boolean v) { this.autoNutritionBody = v; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { this.sortOrder = v; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
