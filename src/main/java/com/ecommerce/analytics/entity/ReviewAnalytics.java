package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Read-model row per review. Needed because REVIEW_UPDATED / REVIEW_DELETED
 * events don't carry the previous rating, so we keep it here to adjust
 * product_analytics / category_analytics running sums correctly.
 */
@Entity
@Table(name = "review_analytics", indexes = {
        @Index(name = "idx_review_analytics_product", columnList = "product_id"),
        @Index(name = "idx_review_analytics_user", columnList = "user_id")
})
public class ReviewAnalytics {

    @Id
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ReviewAnalytics() {
    }

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
