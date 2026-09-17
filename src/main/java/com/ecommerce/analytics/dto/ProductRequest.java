package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(
        name = "ProductRequest",
        description = "Request payload used to create or update a product"
)
public class ProductRequest {

    @Schema(
            description = "Name of the product",
            example = "Wireless Headphones",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Product name is required")
    private String name;

    @Schema(
            description = "Price of the product",
            example = "4999.99",
            minimum = "0.01",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Product price is required")
    @Positive(message = "Product price must be greater than 0")
    private Double price;

    @Schema(
            description = "ID of the category associated with the product",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Category ID is required")
    private Long categoryId;

    public ProductRequest() {
    }

    public ProductRequest(
            String name,
            Double price,
            Long categoryId
    ) {
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
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