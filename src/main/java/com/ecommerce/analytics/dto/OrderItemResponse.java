package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "OrderItemResponse",
        description = "Information about a product included in an order"
)
public class OrderItemResponse {

    @Schema(
            description = "Unique ID of the order item",
            example = "1"
    )
    private Long id;

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
            description = "Quantity ordered",
            example = "2"
    )
    private Integer quantity;

    @Schema(
            description = "Price of one unit of the product",
            example = "799.99"
    )
    private Double unitPrice;

    public OrderItemResponse() {
    }

    public OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            Integer quantity,
            Double unitPrice
    ) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }
}