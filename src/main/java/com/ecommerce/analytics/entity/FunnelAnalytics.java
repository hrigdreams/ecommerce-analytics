package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per UTC day: counts of shoppers passing each funnel stage.
 * Stages, in order: views -> cart adds -> checkouts started -> orders created -> orders paid.
 * Each stage's count can only be understood relative to the ones before it
 * (e.g. checkoutStarted / cartItemAdded = start-of-checkout rate).
 */
@Entity
@Table(name = "funnel_analytics", indexes = {
        @Index(name = "idx_funnel_bucket_date", columnList = "bucket_date", unique = true)
})
public class FunnelAnalytics {

    @Id
    @Column(name = "bucket_date", nullable = false)
    private Instant bucketDate; // truncated to UTC day

    @Column(name = "product_views", nullable = false)
    private long productViews = 0;

    @Column(name = "cart_item_added", nullable = false)
    private long cartItemAdded = 0;

    @Column(name = "checkout_started", nullable = false)
    private long checkoutStarted = 0;

    @Column(name = "orders_created", nullable = false)
    private long ordersCreated = 0;

    @Column(name = "orders_paid", nullable = false)
    private long ordersPaid = 0;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public FunnelAnalytics() {
    }

    public Instant getBucketDate() { return bucketDate; }
    public void setBucketDate(Instant bucketDate) { this.bucketDate = bucketDate; }

    public long getProductViews() { return productViews; }
    public void setProductViews(long productViews) { this.productViews = productViews; }

    public long getCartItemAdded() { return cartItemAdded; }
    public void setCartItemAdded(long cartItemAdded) { this.cartItemAdded = cartItemAdded; }

    public long getCheckoutStarted() { return checkoutStarted; }
    public void setCheckoutStarted(long checkoutStarted) { this.checkoutStarted = checkoutStarted; }

    public long getOrdersCreated() { return ordersCreated; }
    public void setOrdersCreated(long ordersCreated) { this.ordersCreated = ordersCreated; }

    public long getOrdersPaid() { return ordersPaid; }
    public void setOrdersPaid(long ordersPaid) { this.ordersPaid = ordersPaid; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
