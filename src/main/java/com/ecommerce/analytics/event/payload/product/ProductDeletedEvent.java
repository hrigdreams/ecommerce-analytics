package com.ecommerce.analytics.event.payload.product;

public class ProductDeletedEvent {

    private Long productId;

    public ProductDeletedEvent() {
    }

    public ProductDeletedEvent(Long productId) {
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}