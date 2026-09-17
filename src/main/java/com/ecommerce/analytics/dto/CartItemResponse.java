package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CartItemResponse",
        description = "Information about an item inside a shopping cart"
)
public class CartItemResponse {

    @Schema(
            description = "Unique ID of the cart item",
            example = "1"
    )
    private Long id;

    @Schema(
            description = "ID of the cart",
            example = "1"
    )
    private Long cartId;

    @Schema(
            description = "ID of the product",
            example = "1"
    )
    private Long productId;

    @Schema(
            description = "Name of the product",
            example = "Laptop"
    )
    private String productName;

    @Schema(
            description = "Quantity of the product",
            example = "2"
    )
    private Integer quantity;

    public CartItemResponse() {
    }

    public CartItemResponse(
            Long id,
            Long cartId,
            Long productId,
            String productName,
            Integer quantity
    ) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCartId() {
        return cartId;
    }

    public void setCartId(Long cartId) {
        this.cartId = cartId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}