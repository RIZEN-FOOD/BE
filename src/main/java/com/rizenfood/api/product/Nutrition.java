package com.rizenfood.api.product;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 영양성분. 법정 표시사항이므로 이미지가 아니라 이 값으로 DOM 텍스트를 만든다.
 *
 * 수치를 지어내지 않는다. 확보되지 않은 항목은 비워두고 화면에서 "확인 후 표기" 로 보여준다.
 */
@Entity
@Table(name = "nutrition")
public class Nutrition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "serving_size_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal servingSizeG;

    @Column(precision = 8, scale = 2) private BigDecimal kcal;
    @Column(name = "carb_g", precision = 8, scale = 2) private BigDecimal carbG;
    @Column(name = "protein_g", precision = 8, scale = 2) private BigDecimal proteinG;
    @Column(name = "fat_g", precision = 8, scale = 2) private BigDecimal fatG;
    @Column(name = "sugar_g", precision = 8, scale = 2) private BigDecimal sugarG;
    @Column(name = "sodium_mg", precision = 8, scale = 2) private BigDecimal sodiumMg;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Nutrition() {
    }

    public Nutrition(BigDecimal servingSizeG, BigDecimal kcal, BigDecimal carbG,
                     BigDecimal proteinG, BigDecimal fatG, BigDecimal sugarG, BigDecimal sodiumMg) {
        this.servingSizeG = servingSizeG;
        this.kcal = kcal;
        this.carbG = carbG;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.sugarG = sugarG;
        this.sodiumMg = sodiumMg;
    }

    void assignTo(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public BigDecimal getServingSizeG() { return servingSizeG; }
    public BigDecimal getKcal() { return kcal; }
    public BigDecimal getCarbG() { return carbG; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getFatG() { return fatG; }
    public BigDecimal getSugarG() { return sugarG; }
    public BigDecimal getSodiumMg() { return sodiumMg; }
}
