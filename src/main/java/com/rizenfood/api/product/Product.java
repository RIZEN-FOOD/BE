package com.rizenfood.api.product;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

/**
 * 상품.
 *
 * 지금 제품은 하나지만 처음부터 다제품 구조다 (기획서 §8).
 * 값은 전부 DB 에서 온다. 코드에 상품을 박지 않는다.
 *
 * 금액은 원 단위 정수다. 부동소수점을 쓰면 반올림 오차로 1원씩 어긋난다.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주소에 쓰는 영문 이름. 바꾸면 공개 URL 이 깨진다. */
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "name_ko", nullable = false, length = 200)
    private String nameKo;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(length = 300)
    private String subtitle;

    /** 에디터 입력. 저장 전에 반드시 HtmlSanitizer 를 거친다. */
    @Column(name = "description_html", columnDefinition = "text")
    private String descriptionHtml;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "discount_price")
    private Integer discountPrice;

    @Column(name = "weight_g")
    private Integer weightG;

    private Integer servings;

    /** 옵션이 있는 상품은 옵션별 재고를 쓴다. 이 값은 옵션 없는 상품용이다. */
    @Column(nullable = false)
    private Integer stock = 0;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    /** 메인 페이지 노출 */
    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** 사이트 노출. false 면 공개 API 가 반환하지 않는다. */
    @Column(nullable = false)
    private boolean visible = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // 상세 응답에 함께 나가는 것들.
    // 목록에서는 쓰지 않으므로 전부 지연 로딩이다.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @BatchSize(size = 100)
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @BatchSize(size = 100)
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @BatchSize(size = 100)
    private List<Ingredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @BatchSize(size = 100)
    private List<PurchaseLink> purchaseLinks = new ArrayList<>();

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Nutrition nutrition;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ProductLabel label;

    protected Product() {
    }

    public Product(String slug, String nameKo, Integer price) {
        this.slug = slug;
        this.nameKo = nameKo;
        this.price = price;
    }

    /** 실제로 팔리는 값. 할인가가 있으면 그쪽이다. */
    public int effectivePrice() {
        return discountPrice != null ? discountPrice : price;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    // ── 연관 편의 메서드 ── 양쪽 참조를 함께 맞춘다 ──
    public void replaceImages(List<ProductImage> next) {
        images.clear();
        next.forEach(i -> { i.assignTo(this); images.add(i); });
    }

    public void replaceOptions(List<ProductOption> next) {
        options.clear();
        next.forEach(o -> { o.assignTo(this); options.add(o); });
    }

    public void replaceIngredients(List<Ingredient> next) {
        ingredients.clear();
        next.forEach(i -> { i.assignTo(this); ingredients.add(i); });
    }

    public void replacePurchaseLinks(List<PurchaseLink> next) {
        purchaseLinks.clear();
        next.forEach(l -> { l.assignTo(this); purchaseLinks.add(l); });
    }

    public void setNutrition(Nutrition next) {
        if (next != null) {
            next.assignTo(this);
        }
        this.nutrition = next;
    }

    public void setLabel(ProductLabel next) {
        if (next != null) {
            next.assignTo(this);
        }
        this.label = next;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getNameKo() { return nameKo; }
    public String getNameEn() { return nameEn; }
    public String getSubtitle() { return subtitle; }
    public String getDescriptionHtml() { return descriptionHtml; }
    public Integer getPrice() { return price; }
    public Integer getDiscountPrice() { return discountPrice; }
    public Integer getWeightG() { return weightG; }
    public Integer getServings() { return servings; }
    public Integer getStock() { return stock; }
    public String getThumbnailKey() { return thumbnailKey; }
    public boolean isFeatured() { return featured; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ProductImage> getImages() { return images; }
    public List<ProductOption> getOptions() { return options; }
    public List<Ingredient> getIngredients() { return ingredients; }
    public List<PurchaseLink> getPurchaseLinks() { return purchaseLinks; }
    public Nutrition getNutrition() { return nutrition; }
    public ProductLabel getLabel() { return label; }

    public void setSlug(String slug) { this.slug = slug; }
    public void setNameKo(String nameKo) { this.nameKo = nameKo; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public void setDescriptionHtml(String descriptionHtml) { this.descriptionHtml = descriptionHtml; }
    public void setPrice(Integer price) { this.price = price; }
    public void setDiscountPrice(Integer discountPrice) { this.discountPrice = discountPrice; }
    public void setWeightG(Integer weightG) { this.weightG = weightG; }
    public void setServings(Integer servings) { this.servings = servings; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setThumbnailKey(String thumbnailKey) { this.thumbnailKey = thumbnailKey; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
