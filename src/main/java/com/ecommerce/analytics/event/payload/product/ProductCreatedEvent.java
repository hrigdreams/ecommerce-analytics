package com.ecommerce.analytics.event.payload.product;

public class ProductCreatedEvent {

    private Long productId;
    private String name;
    private Double price;
    private Long categoryId;

    public ProductCreatedEvent() {
    }

    public ProductCreatedEvent(
            Long productId,
            String name,
            Double price,
            Long categoryId
    ) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
    }

    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public Double getPrice() { return price; }
    public Long getCategoryId() { return categoryId; }

    public void setProductId(Long productId) { this.productId = productId; }
    public void setName(String name) { this.name = name; }
    public void setPrice(Double price) { this.price = price; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}