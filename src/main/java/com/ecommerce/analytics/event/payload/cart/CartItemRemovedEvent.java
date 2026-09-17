package com.ecommerce.analytics.event.payload.cart;

public class CartItemRemovedEvent {

    private Long cartId;
    private Long cartItemId;

    public CartItemRemovedEvent() {
    }

    public CartItemRemovedEvent(Long cartId, Long cartItemId) {
        this.cartId = cartId;
        this.cartItemId = cartItemId;
    }

    public Long getCartId() { return cartId; }
    public Long getCartItemId() { return cartItemId; }

    public void setCartId(Long cartId) { this.cartId = cartId; }
    public void setCartItemId(Long cartItemId) { this.cartItemId = cartItemId; }
}