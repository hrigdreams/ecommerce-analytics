package com.ecommerce.analytics.dto.analytics.unified;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record UnifiedAnalyticsResponse(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "Asia/Kathmandu")
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