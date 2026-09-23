package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.ProductAnalytics;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductAnalyticsResponse(
        Long productId,
        String productName,
        Long categoryId,
        BigDecimal price,
        boolean active,
        long totalViews,
        long unitsSold,
        BigDecimal revenue,
        long cartAddCount,
        long reviewCount,
        double averageRating,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductAnalyticsResponse from(ProductAnalytics e) {
        return new ProductAnalyticsResponse(
                e.getProductId(), e.getProductName(), e.getCategoryId(), e.getPrice(), e.isActive(),
                e.getTotalViews(), e.getUnitsSold(), e.getRevenue(), e.getCartAddCount(),
                e.getReviewCount(), e.getAverageRating(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
