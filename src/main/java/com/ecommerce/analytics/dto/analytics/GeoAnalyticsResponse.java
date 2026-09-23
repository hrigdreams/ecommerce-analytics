package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.GeoAnalytics;

import java.math.BigDecimal;
import java.time.Instant;

public record GeoAnalyticsResponse(
        String country,
        String city,
        long orders,
        long paidOrders,
        BigDecimal revenue,
        Instant updatedAt
) {
    public static GeoAnalyticsResponse from(GeoAnalytics e) {
        return new GeoAnalyticsResponse(
                e.getCountry(), e.getCity(), e.getOrders(), e.getPaidOrders(),
                e.getRevenue(), e.getUpdatedAt()
        );
    }
}
