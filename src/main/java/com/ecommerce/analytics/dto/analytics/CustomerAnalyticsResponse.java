package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.CustomerAnalytics;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerAnalyticsResponse(
        Long userId,
        long totalOrders,
        long paidOrders,
        BigDecimal totalSpend,
        BigDecimal averageOrderValue,
        Instant firstOrderAt,
        Instant lastOrderAt,
        Instant updatedAt
) {
    public static CustomerAnalyticsResponse from(CustomerAnalytics e) {
        return new CustomerAnalyticsResponse(
                e.getUserId(), e.getTotalOrders(), e.getPaidOrders(), e.getTotalSpend(),
                e.getAverageOrderValue(), e.getFirstOrderAt(), e.getLastOrderAt(), e.getUpdatedAt()
        );
    }
}
