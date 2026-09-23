package com.ecommerce.analytics.event.payload.funnel;

/** Emitted when a shopper opens a single product's detail page (GET /products/{id}). */
public class ProductViewedEvent {

    private Long productId;
    private String productName;
    private Long userId; // nullable: anonymous/unauthenticated views are allowed

    public ProductViewedEvent() {
    }

    public ProductViewedEvent(Long productId, String productName, Long userId) {
        this.productId = productId;
        this.productName = productName;
        this.userId = userId;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Long getUserId() { return userId; }

    public void setProductId(Long productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setUserId(Long userId) { this.userId = userId; }
}
