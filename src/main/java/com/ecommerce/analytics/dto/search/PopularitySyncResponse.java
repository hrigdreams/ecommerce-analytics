package com.ecommerce.analytics.dto.search;

/** sourceStatus mirrors SparkSourceStatus (AVAILABLE/EMPTY/TABLE_MISSING) as a plain string for the REST layer. */
public record PopularitySyncResponse(String sourceStatus, int productsUpdated) {
}
