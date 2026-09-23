package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.CategoryAnalytics;

import java.math.BigDecimal;
import java.time.Instant;

public record CategoryAnalyticsResponse(
        Long categoryId,
        String categoryName,
        long productCount,
        long unitsSold,
        BigDecimal revenue,
        long reviewCount,
        double averageRating,
        Instant updatedAt
) {
    public static CategoryAnalyticsResponse from(CategoryAnalytics e) {
        return new CategoryAnalyticsResponse(
                e.getCategoryId(), e.getCategoryName(), e.getProductCount(), e.getUnitsSold(),
                e.getRevenue(), e.getReviewCount(), e.getAverageRating(), e.getUpdatedAt()
        );
    }
}
