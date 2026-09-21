package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Read-model row per customer (user), built from ORDER_CREATED and the
 * PAID branch of PAYMENT_CREATED (via OrderAnalyticsService).
 */
@Entity
@Table(name = "customer_analytics")
public class CustomerAnalytics {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_orders", nullable = false)
    private long totalOrders = 0;

    @Column(name = "paid_orders", nullable = false)
    private long paidOrders = 0;

    @Column(name = "total_spend", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalSpend = BigDecimal.ZERO;

    @Column(name = "first_order_at")
    private Instant firstOrderAt;

    @Column(name = "last_order_at")
    private Instant lastOrderAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public CustomerAnalytics() {
    }

    /** total_spend / paid_orders (revenue only counts paid orders, so the average matches). */
    public BigDecimal getAverageOrderValue() {
        if (paidOrders == 0) {
            return BigDecimal.ZERO;
        }
        return totalSpend.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }

    public long getPaidOrders() { return paidOrders; }
    public void setPaidOrders(long paidOrders) { this.paidOrders = paidOrders; }

    public BigDecimal getTotalSpend() { return totalSpend; }
    public void setTotalSpend(BigDecimal totalSpend) { this.totalSpend = totalSpend; }

    public Instant getFirstOrderAt() { return firstOrderAt; }
    public void setFirstOrderAt(Instant firstOrderAt) { this.firstOrderAt = firstOrderAt; }

    public Instant getLastOrderAt() { return lastOrderAt; }
    public void setLastOrderAt(Instant lastOrderAt) { this.lastOrderAt = lastOrderAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
