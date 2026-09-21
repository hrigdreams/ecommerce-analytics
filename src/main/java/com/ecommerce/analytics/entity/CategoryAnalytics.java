package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-model row per category, rolled up from ProductAnalytics whenever a
 * product is created/deleted, sold (paid order), or reviewed.
 */
@Entity
@Table(name = "category_analytics")
public class CategoryAnalytics {

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "product_count", nullable = false)
    private long productCount = 0;

    @Column(name = "units_sold", nullable = false)
    private long unitsSold = 0;

    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private long reviewCount = 0;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum = 0;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public CategoryAnalytics() {
    }

    public double getAverageRating() {
        return reviewCount == 0 ? 0.0 : (double) ratingSum / reviewCount;
    }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public long getProductCount() { return productCount; }
    public void setProductCount(long productCount) { this.productCount = Math.max(productCount, 0); }

    public long getUnitsSold() { return unitsSold; }
    public void setUnitsSold(long unitsSold) { this.unitsSold = unitsSold; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public long getReviewCount() { return reviewCount; }
    public void setReviewCount(long reviewCount) { this.reviewCount = Math.max(reviewCount, 0); }

    public long getRatingSum() { return ratingSum; }
    public void setRatingSum(long ratingSum) { this.ratingSum = Math.max(ratingSum, 0); }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
