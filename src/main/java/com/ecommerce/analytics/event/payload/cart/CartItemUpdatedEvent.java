package com.ecommerce.analytics.event.payload.cart;

public class CartItemUpdatedEvent {

    private Long cartId;
    private Long cartItemId;
    private Long productId;
    private Integer quantity;

    public CartItemUpdatedEvent() {
    }

    public CartItemUpdatedEvent(
            Long cartId,
            Long cartItemId,
            Long productId,
            Integer quantity
    ) {
        this.cartId = cartId;
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getCartId() { return cartId; }
    public Long getCartItemId() { return cartItemId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }

    public void setCartId(Long cartId) { this.cartId = cartId; }
    public void setCartItemId(Long cartItemId) { this.cartItemId = cartItemId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}