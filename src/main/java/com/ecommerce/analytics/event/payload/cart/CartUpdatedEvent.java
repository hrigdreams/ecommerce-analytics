package com.ecommerce.analytics.event.payload.cart;

public class CartUpdatedEvent {

    private Long cartId;
    private Long userId;

    public CartUpdatedEvent() {
    }

    public CartUpdatedEvent(Long cartId, Long userId) {
        this.cartId = cartId;
        this.userId = userId;
    }

    public Long getCartId() { return cartId; }
    public Long getUserId() { return userId; }

    public void setCartId(Long cartId) { this.cartId = cartId; }
    public void setUserId(Long userId) { this.userId = userId; }
}