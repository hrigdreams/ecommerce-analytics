package com.ecommerce.analytics.dto.analytics.unified;

import java.time.Instant;

// sanity check
public record UnifiedAnalyticsResponse(
        Instant generatedAt,
        CustomerSummarySection customerSummary,
        SparkSection revenueTrend,
        SparkSection productPerformance,
        SparkSection categoryPerformance,
        SparkSection geoPerformance,
        SparkSection liveEventCounts,
        SparkSection liveOrderActivity,
        SparkSection livePaidRevenue
) {
}
