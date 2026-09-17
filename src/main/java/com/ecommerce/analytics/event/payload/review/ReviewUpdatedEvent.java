package com.ecommerce.analytics.event.payload.review;

public class ReviewUpdatedEvent {

    private Long reviewId;
    private Long userId;
    private Long productId;
    private Integer rating;
    private String comment;

    public ReviewUpdatedEvent() {
    }

    public ReviewUpdatedEvent(
            Long reviewId,
            Long userId,
            Long productId,
            Integer rating,
            String comment
    ) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.productId = productId;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getReviewId() { return reviewId; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }

    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setComment(String comment) { this.comment = comment; }
}