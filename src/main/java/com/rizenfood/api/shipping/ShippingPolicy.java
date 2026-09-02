package com.rizenfood.api.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 배송비 정책.
 *
 * ★ 배송비 임계액을 코드에 박지 않는다 (CLAUDE.md 규칙 5).
 *   기본 배송비와 무료배송 임계액은 이 테이블에서 읽어, 대표가 관리자에서 바꾼다.
 *
 * 활성 정책은 하나만 둔다 (스키마의 부분 유니크 인덱스가 강제한다).
 * 이 엔티티는 조회 전용이다 — 쓰기는 관리자 정책 화면이 생길 때 별도로 붙인다.
 */
@Entity
@Table(name = "shipping_policy")
public class ShippingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 기본 배송비(원). */
    @Column(name = "base_fee", nullable = false)
    private int baseFee;

    /** 이 금액(원) 이상이면 배송비 0. null 이면 무료배송 없음. */
    @Column(name = "free_threshold")
    private Integer freeThreshold;

    @Column(name = "island_extra_fee", nullable = false)
    private int islandExtraFee;

    @Column(nullable = false)
    private boolean visible;

    protected ShippingPolicy() {
    }

    /**
     * 상품금액에 대해 물릴 배송비를 계산한다.
     * 상품금액이 0 이면(빈 장바구니) 배송비도 0 이다.
     */
    public int feeFor(int itemsAmount) {
        if (itemsAmount <= 0) {
            return 0;
        }
        if (freeThreshold != null && itemsAmount >= freeThreshold) {
            return 0;
        }
        return baseFee;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getBaseFee() {
        return baseFee;
    }

    public Integer getFreeThreshold() {
        return freeThreshold;
    }

    public int getIslandExtraFee() {
        return islandExtraFee;
    }

    public boolean isVisible() {
        return visible;
    }
}
