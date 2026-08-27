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

import java.math.BigDecimal;

/**
 * 원재료명 및 함량. 알레르기 유발물질 표시 의무 대응.
 * 이미지가 아니라 이 값으로 DOM 텍스트를 만든다 (CLAUDE.md 규칙 2).
 */
@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(length = 120)
    private String origin;

    @Column(length = 200)
    private String allergen;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected Ingredient() {
    }

    public Ingredient(String name, BigDecimal percentage, String origin, String allergen, int sortOrder) {
        this.name = name;
        this.percentage = percentage;
        this.origin = origin;
        this.allergen = allergen;
        this.sortOrder = sortOrder;
    }

    void assignTo(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPercentage() { return percentage; }
    public String getOrigin() { return origin; }
    public String getAllergen() { return allergen; }
    public int getSortOrder() { return sortOrder; }
}
