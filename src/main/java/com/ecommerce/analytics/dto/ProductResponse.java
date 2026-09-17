package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ProductResponse",
        description = "Response containing product information"
)
public class ProductResponse {

    @Schema(
            description = "Unique identifier of the product",
            example = "1"
    )
    private Long id;

    @Schema(
            description = "Name of the product",
            example = "Wireless Headphones"
    )
    private String name;

    @Schema(
            description = "Price of the product",
            example = "4999.99"
    )
    private Double price;

    @Schema(
            description = "ID of the category associated with the product",
            example = "1"
    )
    private Long categoryId;

    public ProductResponse() {
    }

    public ProductResponse(
            Long id,
            String name,
            Double price,
            Long categoryId
    ) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}