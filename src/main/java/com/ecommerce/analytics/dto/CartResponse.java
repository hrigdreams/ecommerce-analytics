package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CartResponse",
        description = "Shopping cart information"
)
public class CartResponse {

    @Schema(
            description = "Unique ID of the cart",
            example = "1"
    )
    private Long id;

    @Schema(
            description = "ID of the user who owns the cart",
            example = "1"
    )
    private Long userId;

    public CartResponse() {
    }

    public CartResponse(Long id, Long userId) {
        this.id = id;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}