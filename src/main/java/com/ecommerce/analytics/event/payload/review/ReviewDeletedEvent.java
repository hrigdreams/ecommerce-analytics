package com.ecommerce.analytics.event.payload.review;

public class ReviewDeletedEvent {

    private Long reviewId;
    private Long userId;
    private Long productId;

    public ReviewDeletedEvent() {
    }

    public ReviewDeletedEvent(
            Long reviewId,
            Long userId,
            Long productId
    ) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.productId = productId;
    }

    public Long getReviewId() { return reviewId; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }

    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setProductId(Long productId) { this.productId = productId; }
}