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
 * 용량·맛 옵션. 지금은 안 쓰지만 구조를 열어둔다 (기획서 §13-11).
 * price_delta 는 기준가 대비 증감액이다.
 */
@Entity
@Table(name = "product_option")
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "price_delta", nullable = false)
    private int priceDelta = 0;

    @Column(nullable = false)
    private int stock = 0;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean visible = true;

    protected ProductOption() {
    }

    public ProductOption(String name, int priceDelta, int stock, int sortOrder, boolean visible) {
        this.name = name;
        this.priceDelta = priceDelta;
        this.stock = stock;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    void assignTo(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getPriceDelta() { return priceDelta; }
    public int getStock() { return stock; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
}
