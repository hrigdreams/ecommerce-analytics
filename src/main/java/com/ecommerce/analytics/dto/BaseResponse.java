package com.ecommerce.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Common base for response DTOs that represent a persisted entity - today
 * just the one field every such response shares (the entity's own database
 * id). New response DTOs (CategoryResponse, OrderResponse, ...) can extend
 * this the same way ProductResponse does below, instead of redeclaring
 * id/getId/setId themselves.
 */
public abstract class BaseResponse {

    @Schema(description = "Unique identifier of the resource", example = "1")
    protected Long id;

    protected BaseResponse() {
    }

    protected BaseResponse(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseResponse that = (BaseResponse) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
