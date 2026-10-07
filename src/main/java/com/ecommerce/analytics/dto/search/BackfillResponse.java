package com.ecommerce.analytics.dto.search;

public record BackfillResponse(int productsIndexed, int pagesProcessed, String targetIndex) {
}
