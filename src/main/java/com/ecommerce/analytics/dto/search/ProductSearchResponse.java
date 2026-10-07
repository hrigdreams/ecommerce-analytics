package com.ecommerce.analytics.dto.search;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import java.util.List;

public record ProductSearchResponse(
        List<?> hits,
        long totalHits,
        int totalPages,
        List<CategoryBucket> categoryBuckets,
        List<PriceRangeBucket> priceRangeBuckets,
        int page,
        int size
) {
    // Legacy constructor for ProductSearchService calls without bucket arguments
    public ProductSearchResponse(List<?> hits, long totalHits, int page, int size) {
        this(
                hits,
                totalHits,
                size == 0 ? 0 : (int) Math.ceil((double) totalHits / size),
                List.of(),
                List.of(),
                page,
                size
        );
    }

    // Secondary constructor for Native Search calls
    public ProductSearchResponse(
            List<?> hits,
            long totalHits,
            int totalPages,
            List<CategoryBucket> categoryBuckets,
            List<PriceRangeBucket> priceRangeBuckets) {
        this(hits, totalHits, totalPages, categoryBuckets, priceRangeBuckets, 0, 10);
    }
}