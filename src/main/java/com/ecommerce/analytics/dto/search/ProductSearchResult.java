package com.ecommerce.analytics.dto.search;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;

public record ProductSearchResult(
        Long id,
        String name,
        Double price,
        Long categoryId,
        String categoryName,
        Double averageRating,
        Integer reviewCount
) {
    public static ProductSearchResult from(ProductDocument doc) {
        return new ProductSearchResult(
                doc.getId(), doc.getName(), doc.getPrice(), doc.getCategoryId(),
                doc.getCategoryName(), doc.getAverageRating(), doc.getReviewCount()
        );
    }
}
