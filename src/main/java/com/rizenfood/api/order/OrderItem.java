package com.rizenfood.api.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;

/**
 * 주문 항목.
 *
 * 상품·옵션 참조는 남기되(통계용), 표시에 필요한 값은 전부 스냅샷으로 박는다.
 * 상품이 수정·삭제돼도 주문서에는 주문 당시의 이름·가격이 그대로 남아야 한다.
 * DB CHECK 가 line_amount = unit_price_snapshot * quantity 를 강제한다.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 상품이 지워지면 null 이 된다(스키마 SET NULL). 이름은 스냅샷에 있다. */
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_option_id")
    private Long productOptionId;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "option_name_snapshot", length = 120)
    private String optionNameSnapshot;

    @Column(name = "thumbnail_key_snapshot", length = 500)
    private String thumbnailKeySnapshot;

    @Column(name = "unit_price_snapshot", nullable = false)
    private int unitPriceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_amount", nullable = false)
    private int lineAmount;

    /** 바깥 서비스의 상품주문번호 (V36). 네이버페이는 발송·취소·반품을 이 번호로 주고받는다. */
    @Column(name = "external_product_order_id", length = 64)
    private String externalProductOrderId;

    protected OrderItem() {
    }

    public OrderItem(Long productId, Long productOptionId, String productNameSnapshot,
                     String optionNameSnapshot, String thumbnailKeySnapshot,
                     int unitPriceSnapshot, int quantity) {
        this.productId = productId;
        this.productOptionId = productOptionId;
        this.productNameSnapshot = productNameSnapshot;
        this.optionNameSnapshot = optionNameSnapshot;
        this.thumbnailKeySnapshot = thumbnailKeySnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
        this.lineAmount = unitPriceSnapshot * quantity;
    }

    void attachTo(Order order) {
        this.order = order;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getProductOptionId() { return productOptionId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getOptionNameSnapshot() { return optionNameSnapshot; }
    public String getThumbnailKeySnapshot() { return thumbnailKeySnapshot; }
    public int getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public int getQuantity() { return quantity; }
    public int getLineAmount() { return lineAmount; }
    public String getExternalProductOrderId() { return externalProductOrderId; }
    public void setExternalProductOrderId(String v) { this.externalProductOrderId = v; }
}
