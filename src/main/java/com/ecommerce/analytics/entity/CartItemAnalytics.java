package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per CART_ITEM_ADDED, kept (not deleted) on removal so cart-history /
 * product-cart-frequency queries have something to read. cartItemId is the
 * source-system id from the event and is how updates/removals are located.
 */
@Entity
@Table(name = "cart_item_analytics", indexes = {
        @Index(name = "idx_cia_cart", columnList = "cart_id"),
        @Index(name = "idx_cia_product", columnList = "product_id"),
        @Index(name = "idx_cia_cart_item", columnList = "cart_item_id", unique = true)
})
public class CartItemAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "cart_item_id", nullable = false)
    private Long cartItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "added_at")
    private Instant addedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    public CartItemAnalytics() {
    }

    public Long getId() { return id; }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public Long getCartItemId() { return cartItemId; }
    public void setCartItemId(Long cartItemId) { this.cartItemId = cartItemId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getRemovedAt() { return removedAt; }
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }
}
