package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Read-model row per cart, built from CART_* and CART_ITEM_* events.
 * item_count tracks currently-active (not removed) items and is what
 * "cart abandonment" queries filter on (active cart, item_count > 0,
 * stale last_activity_at).
 */
@Entity
@Table(name = "cart_analytics", indexes = {
        @Index(name = "idx_cart_analytics_user", columnList = "user_id"),
        @Index(name = "idx_cart_analytics_last_activity", columnList = "last_activity_at")
})
public class CartAnalytics {

    @Id
    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CartAnalytics() {
    }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = Math.max(itemCount, 0); }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
