package com.ecommerce.analytics.event.payload.order;

public class OrderItemEvent {

    private Long orderItemId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;

    public OrderItemEvent() {
    }

    public OrderItemEvent(
            Long orderItemId,
            Long productId,
            String productName,
            Integer quantity,
            Double unitPrice
    ) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getOrderItemId() { return orderItemId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public Double getUnitPrice() { return unitPrice; }

    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
}