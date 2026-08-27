package com.rizenfood.api.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 상품 이미지 한 장.
 * image_key 는 S3 오브젝트 키다. 앱 서버가 파일을 서빙하지 않는다.
 */
@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    /** 접근성 필수. 비어 있으면 스크린리더가 파일명을 읽는다. */
    @Column(name = "alt_text", length = 300)
    private String altText;

    /** MAIN | DETAIL | LIFESTYLE */
    @Column(nullable = false, length = 20)
    private String type = "DETAIL";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected ProductImage() {
    }

    public ProductImage(String imageKey, String altText, String type, int sortOrder) {
        this.imageKey = imageKey;
        this.altText = altText;
        this.type = type;
        this.sortOrder = sortOrder;
    }

    void assignTo(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public String getImageKey() { return imageKey; }
    public String getAltText() { return altText; }
    public String getType() { return type; }
    public int getSortOrder() { return sortOrder; }
}
