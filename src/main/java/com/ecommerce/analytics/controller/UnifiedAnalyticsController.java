package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.analytics.unified.UnifiedAnalyticsResponse;
import com.ecommerce.analytics.service.UnifiedAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// keeping this simple
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Unified Analytics", description = "Single composed view across Spring, Spark batch, and Spark streaming analytics")
public class UnifiedAnalyticsController {

    private final UnifiedAnalyticsService unifiedAnalyticsService;

    public UnifiedAnalyticsController(UnifiedAnalyticsService unifiedAnalyticsService) {
        this.unifiedAnalyticsService = unifiedAnalyticsService;
    }

    @Operation(
            summary = "Unified analytics snapshot",
            description = "Composes Spring's customer summary with Spark batch trend/performance sections and Spark " +
                    "streaming live-activity sections into one payload. Every section carries its own source/status/asOf " +
                    "so callers can tell what backs each number and how fresh it is - Spark sections whose owning job " +
                    "has never run report status UNAVAILABLE rather than being silently omitted or backfilled from " +
                    "Spring data. Cached for 30 seconds."
    )
    @GetMapping("/unified")
    public UnifiedAnalyticsResponse getUnifiedAnalytics(
            @Parameter(description = "Days of revenue trend to include (spark_batch_revenue_trend), most recent first")
            @RequestParam(defaultValue = "30") int trendDays,
            @Parameter(description = "Top-N cap for product/geo performance sections")
            @RequestParam(defaultValue = "10") int topN,
            @Parameter(description = "How many recent closed streaming windows to include per live section")
            @RequestParam(defaultValue = "10") int liveWindows
    ) {
        int safeTrendDays = clamp(trendDays, 1, 365);
        int safeTopN = clamp(topN, 1, 100);
        int safeLiveWindows = clamp(liveWindows, 1, 100);

        return unifiedAnalyticsService.get(safeTrendDays, safeTopN, safeLiveWindows);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
