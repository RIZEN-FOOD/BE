package com.rizenfood.api.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_image")
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ReviewImage() {
    }

    public ReviewImage(String imageKey, int sortOrder) {
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }

    void assignTo(Review review) {
        this.review = review;
    }

    public Long getId() { return id; }
    public String getImageKey() { return imageKey; }
    public int getSortOrder() { return sortOrder; }
}
