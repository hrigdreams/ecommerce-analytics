package com.ecommerce.analytics.event.payload.cart;

public class CartItemAddedEvent {

    private Long cartId;
    private Long cartItemId;
    private Long userId;
    private Long productId;
    private String productName;
    private Integer quantity;

    public CartItemAddedEvent() {
    }

    public CartItemAddedEvent(
            Long cartId,
            Long cartItemId,
            Long userId,
            Long productId,
            String productName,
            Integer quantity
    ) {
        this.cartId = cartId;
        this.cartItemId = cartItemId;
        this.userId = userId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getCartId() { return cartId; }
    public Long getCartItemId() { return cartItemId; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }

    public void setCartId(Long cartId) { this.cartId = cartId; }
    public void setCartItemId(Long cartItemId) { this.cartItemId = cartItemId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}