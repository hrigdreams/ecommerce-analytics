package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        name = "CategoryRequest",
        description = "Request payload used to create or update a category"
)
public class CategoryRequest {

    @Schema(
            description = "Name of the category",
            example = "Electronics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Category name is required")
    private String name;

    public CategoryRequest() {
    }

    public CategoryRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}