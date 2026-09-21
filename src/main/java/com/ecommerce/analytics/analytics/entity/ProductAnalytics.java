package com.ecommerce.analytics.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-model row per product. Identity/price fields come from PRODUCT_* events;
 * metric fields (views, sales, revenue, ratings) are filled by later events.
 */
@Entity
@Table(name = "product_analytics")
public class ProductAnalytics {

    @Id
    @Column(name = "product_id")
    private Long productId;          // same value as products.id (not generated here)

    @Column(name = "product_name")
    private String productName;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;   // false after PRODUCT_DELETED (soft delete)

    @Column(name = "total_views", nullable = false)
    private long totalViews = 0;

    @Column(name = "units_sold", nullable = false)
    private long unitsSold = 0;

    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private long reviewCount = 0;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ProductAnalytics() {
    }

    public double getAverageRating() {
        return reviewCount == 0 ? 0.0 : (double) ratingSum / reviewCount;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }

    public long getUnitsSold() { return unitsSold; }
    public void setUnitsSold(long unitsSold) { this.unitsSold = unitsSold; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public long getReviewCount() { return reviewCount; }
    public void setReviewCount(long reviewCount) { this.reviewCount = reviewCount; }

    public long getRatingSum() { return ratingSum; }
    public void setRatingSum(long ratingSum) { this.ratingSum = ratingSum; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
