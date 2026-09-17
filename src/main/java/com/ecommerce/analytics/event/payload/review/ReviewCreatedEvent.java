package com.ecommerce.analytics.event.payload.review;

import java.time.Instant;

public class ReviewCreatedEvent {

    private Long reviewId;
    private Long userId;
    private Long productId;
    private Integer rating;
    private String comment;
    private Instant createdAt;

    public ReviewCreatedEvent() {
    }

    public ReviewCreatedEvent(
            Long reviewId,
            Long userId,
            Long productId,
            Integer rating,
            String comment,
            Instant createdAt
    ) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.productId = productId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getReviewId() { return reviewId; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }

    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setComment(String comment) { this.comment = comment; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}