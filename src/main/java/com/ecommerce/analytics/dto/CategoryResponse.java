package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CategoryResponse",
        description = "Response containing category information"
)
public class CategoryResponse {

    @Schema(
            description = "Unique identifier of the category",
            example = "1"
    )
    private Long id;

    @Schema(
            description = "Name of the category",
            example = "Electronics"
    )
    private String name;

    public CategoryResponse() {
    }

    public CategoryResponse(Long id, String name) {
        this.id = id;
        this.name = name;
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
}