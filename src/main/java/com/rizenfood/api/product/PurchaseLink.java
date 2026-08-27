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
 * 외부 판매 채널 링크. 자사몰과 병행 판매할 때 쓴다.
 */
@Entity
@Table(name = "purchase_link")
public class PurchaseLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** NAVER | COUPANG | OWN | OTHER */
    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 120)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean visible = true;

    protected PurchaseLink() {
    }

    public PurchaseLink(String channel, String url, String label, int sortOrder, boolean visible) {
        this.channel = channel;
        this.url = url;
        this.label = label;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    void assignTo(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public String getChannel() { return channel; }
    public String getUrl() { return url; }
    public String getLabel() { return label; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
}
