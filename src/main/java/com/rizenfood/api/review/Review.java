package com.rizenfood.api.review;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.rizenfood.api.product.Product;

/**
 * 후기.
 *
 * ★ 후기도 광고 규제 대상이다 (기획서 §9). 사용자가 쓴 글이라도
 *   효능을 단정하는 표현은 노출 전 검토·편집해야 한다. 그래서 visible
 *   기본값이 false 다 — 관리자가 확인해야 공개된다.
 *
 * 탈퇴해도 후기는 남아야 하므로 작성자 이름을 authorName 에 스냅샷으로 박는다.
 * member 연관은 회원 탈퇴 시 SET NULL 되지만 authorName 은 그대로 남는다.
 *
 * order_item 연동(구매 인증)은 주문 기능이 생기면 채워진다. 지금은 항상 null.
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 작성자 회원 id. 탈퇴하면 null 이 된다(스키마 SET NULL). */
    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 작성 시점 이름 스냅샷. 회원이 탈퇴해도 후기에는 이 이름이 남는다. */
    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase = false;

    @Column(name = "is_sponsored", nullable = false)
    private boolean sponsored = false;

    /** 관리자가 확인하기 전에는 공개되지 않는다. */
    @Column(nullable = false)
    private boolean visible = false;

    @Column(name = "hidden_reason", length = 300)
    private String hiddenReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ReviewImage> images = new ArrayList<>();

    protected Review() {
    }

    public Review(Product product, Long memberId, short rating, String content, String authorName) {
        this.product = product;
        this.memberId = memberId;
        this.rating = rating;
        this.content = content;
        this.authorName = authorName;
    }

    public void replaceImages(List<String> imageKeys) {
        images.clear();
        int order = 0;
        for (String key : imageKeys) {
            ReviewImage img = new ReviewImage(key, order++);
            img.assignTo(this);
            images.add(img);
        }
    }

    /** 관리자 승인. 노출을 켜고 사유를 지운다. */
    public void approve() {
        this.visible = true;
        this.hiddenReason = null;
        this.updatedAt = Instant.now();
    }

    /** 관리자가 숨긴다. 사유를 남겨 나중에 왜 숨겼는지 알 수 있게 한다. */
    public void hide(String reason) {
        this.visible = false;
        this.hiddenReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Long getMemberId() { return memberId; }
    public short getRating() { return rating; }
    public String getContent() { return content; }
    public String getAuthorName() { return authorName; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public boolean isSponsored() { return sponsored; }
    public boolean isVisible() { return visible; }
    public String getHiddenReason() { return hiddenReason; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ReviewImage> getImages() { return images; }
}
