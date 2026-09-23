package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.TimeAnalytics;

import java.math.BigDecimal;
import java.time.Instant;

public record TimeAnalyticsResponse(
        String bucketType,
        Instant bucketStart,
        long orders,
        long paidOrders,
        BigDecimal revenue,
        long unitsSold,
        Instant updatedAt
) {
    public static TimeAnalyticsResponse from(TimeAnalytics e) {
        return new TimeAnalyticsResponse(
                e.getBucketType(), e.getBucketStart(), e.getOrders(), e.getPaidOrders(),
                e.getRevenue(), e.getUnitsSold(), e.getUpdatedAt()
        );
    }
}
