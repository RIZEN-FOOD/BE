package com.rizenfood.api.wishlist;

import java.time.Instant;

import com.rizenfood.api.product.Product;

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
 * 위시리스트 항목. 회원 전용(비회원은 쓰지 않는다).
 * 같은 회원이 같은 상품을 두 번 담지 못한다(스키마 유니크).
 */
@Entity
@Table(name = "wishlist_item")
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected WishlistItem() {
    }

    public WishlistItem(Long memberId, Product product) {
        this.memberId = memberId;
        this.product = product;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Product getProduct() { return product; }
    public Instant getCreatedAt() { return createdAt; }
}
