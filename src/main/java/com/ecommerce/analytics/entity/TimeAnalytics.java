package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Rollup row for one time bucket (hour/day/week/month/year), keyed by
 * (bucket_type, bucket_start). Fed by OrderAnalyticsService using the
 * order's own created_at, so an order and its eventual payment always land
 * in the same bucket regardless of when the PAID event actually arrives.
 */
@Entity
@Table(name = "time_analytics", indexes = {
        @Index(name = "idx_time_analytics_bucket", columnList = "bucket_type,bucket_start", unique = true)
})
public class TimeAnalytics {

    public static final String HOUR = "HOUR";
    public static final String DAY = "DAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";
    public static final String YEAR = "YEAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_type", nullable = false)
    private String bucketType;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "orders", nullable = false)
    private long orders = 0;

    @Column(name = "paid_orders", nullable = false)
    private long paidOrders = 0;

    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "units_sold", nullable = false)
    private long unitsSold = 0;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public TimeAnalytics() {
    }

    /** revenue / paid_orders. */
    public BigDecimal getAverageOrderValue() {
        if (paidOrders == 0) {
            return BigDecimal.ZERO;
        }
        return revenue.divide(BigDecimal.valueOf(paidOrders), 2, java.math.RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }

    public String getBucketType() { return bucketType; }
    public void setBucketType(String bucketType) { this.bucketType = bucketType; }

    public Instant getBucketStart() { return bucketStart; }
    public void setBucketStart(Instant bucketStart) { this.bucketStart = bucketStart; }

    public long getOrders() { return orders; }
    public void setOrders(long orders) { this.orders = orders; }

    public long getPaidOrders() { return paidOrders; }
    public void setPaidOrders(long paidOrders) { this.paidOrders = paidOrders; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public long getUnitsSold() { return unitsSold; }
    public void setUnitsSold(long unitsSold) { this.unitsSold = unitsSold; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
