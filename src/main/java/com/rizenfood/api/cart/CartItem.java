package com.rizenfood.api.cart;

import java.time.Instant;

import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductOption;

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
 * 장바구니 항목.
 *
 * 가격을 여기 저장하지 않는다. 장바구니를 보여줄 때마다 상품 테이블에서 현재
 * 가격을 다시 읽어 계산한다 (CLAUDE.md 규칙 5 — 금액을 클라이언트에서 받지 마라,
 * 그리고 담아둔 사이 가격이 바뀌었을 수 있다). 스냅샷은 주문 확정 시에만 박는다.
 *
 * 같은 상품·옵션은 한 줄로 묶는다 (스키마 유니크 인덱스). 다시 담으면 수량을 더한다.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id")
    private ProductOption option;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CartItem() {
    }

    public CartItem(Cart cart, Product product, ProductOption option, int quantity) {
        this.cart = cart;
        this.product = product;
        this.option = option;
        this.quantity = quantity;
    }

    public void addQuantity(int delta) {
        this.quantity += delta;
        this.updatedAt = Instant.now();
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public ProductOption getOption() {
        return option;
    }

    public int getQuantity() {
        return quantity;
    }
}
