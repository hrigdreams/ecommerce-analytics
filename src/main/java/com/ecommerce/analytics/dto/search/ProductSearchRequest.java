package com.ecommerce.analytics.dto.search;

public record ProductSearchRequest(
        String q,
        Long categoryId,
        Double minPrice,
        Double maxPrice,
        int page,
        int size
) {
}