package com.rizenfood.api.product;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 상품 상세 페이지 한 조각.
 *
 * 사진형 상세페이지를 위해 이미지·영상·글을 순서대로 쌓는다. 이미지는 화면에서 틈 없이 이어 붙는다.
 * 영상은 유튜브 주소이거나, 관리자가 올린 mp4 파일이다.
 */
@Entity
@Table(name = "product_detail_section")
public class ProductDetailSection {

    public enum Type { IMAGE, VIDEO, TEXT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 20)
    private String type = Type.IMAGE.name();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    @Column(name = "video_file_key", length = 500)
    private String videoFileKey;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    @Column(length = 200)
    private String heading;

    @Column(columnDefinition = "text")
    private String body;

    @Column(length = 500)
    private String caption;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProductDetailSection() {
    }

    public ProductDetailSection(Product product, String type) {
        this.product = product;
        this.type = type;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getType() { return type; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
    public String getImageKey() { return imageKey; }
    public String getAltText() { return altText; }
    public String getVideoUrl() { return videoUrl; }
    public String getVideoFileKey() { return videoFileKey; }
    public String getThumbnailKey() { return thumbnailKey; }
    public String getHeading() { return heading; }
    public String getBody() { return body; }
    public String getCaption() { return caption; }

    public void setType(String v) { this.type = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setVisible(boolean v) { this.visible = v; }
    public void setImageKey(String v) { this.imageKey = blankToNull(v); }
    public void setAltText(String v) { this.altText = blankToNull(v); }
    public void setVideoUrl(String v) { this.videoUrl = blankToNull(v); }
    public void setVideoFileKey(String v) { this.videoFileKey = blankToNull(v); }
    public void setThumbnailKey(String v) { this.thumbnailKey = blankToNull(v); }
    public void setHeading(String v) { this.heading = blankToNull(v); }
    public void setBody(String v) { this.body = blankToNull(v); }
    public void setCaption(String v) { this.caption = blankToNull(v); }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
