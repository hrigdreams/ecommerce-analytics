package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.analytics.FunnelAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.OrderValueSummaryResponse;
import com.ecommerce.analytics.dto.analytics.unified.DataSource;
import com.ecommerce.analytics.dto.analytics.unified.SparkSection;
import com.ecommerce.analytics.repository.FunnelAnalyticsRepository;
import com.ecommerce.analytics.repository.OrderAnalyticsRepository;
import com.ecommerce.analytics.repository.SparkAnalyticsJdbcRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * PHASE 1, item 5 ("Autonomous backend expansions" - conversion funnel
 * metrics, peak shopping hour summaries, average order value). Kept as its
 * own controller rather than added to the existing AnalyticsController (read
 * models) or UnifiedAnalyticsController (the dashboard's one-shot combined
 * payload): all three endpoints here were deliberately found to require NO
 * new computation logic, only new read paths over data that was already
 * being computed correctly elsewhere (see each method's javadoc) - keeping
 * them separate makes that traceable, rather than mixing "new Phase 1 reads"
 * into the two existing controllers' established endpoint lists.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(
        name = "Advanced Analytics",
        description = "Phase 1: conversion funnel, peak shopping hours, and average order value - " +
                "all backed by existing, already-correct read models; see each endpoint's description."
)
public class AdvancedAnalyticsController {

    private final FunnelAnalyticsRepository funnelAnalyticsRepository;
    private final SparkAnalyticsJdbcRepository sparkAnalyticsJdbcRepository;
    private final OrderAnalyticsRepository orderAnalyticsRepository;

    public AdvancedAnalyticsController(
            FunnelAnalyticsRepository funnelAnalyticsRepository,
            SparkAnalyticsJdbcRepository sparkAnalyticsJdbcRepository,
            OrderAnalyticsRepository orderAnalyticsRepository
    ) {
        this.funnelAnalyticsRepository = funnelAnalyticsRepository;
        this.sparkAnalyticsJdbcRepository = sparkAnalyticsJdbcRepository;
        this.orderAnalyticsRepository = orderAnalyticsRepository;
    }

    @Operation(
            summary = "Conversion funnel (views -> cart -> checkout -> order -> paid)",
            description = "Daily funnel rows with stage-to-stage conversion rates, from FunnelAnalytics " +
                    "(already populated by the Kafka consumer pipeline - FunnelAnalyticsService - this " +
                    "endpoint only adds a read path over it, it was never missing data). Defaults to the " +
                    "last 30 days, newest first."
    )
    @GetMapping("/funnel")
    public List<FunnelAnalyticsResponse> getFunnel(
            @Parameter(description = "ISO-8601 instant, inclusive. Defaults to 30 days ago.")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "ISO-8601 instant, exclusive. Defaults to now.")
            @RequestParam(required = false) Instant to
    ) {
        Instant rangeTo = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.minus(30, ChronoUnit.DAYS);

        return funnelAnalyticsRepository.findByBucketDateBetweenOrderByBucketDateDesc(rangeFrom, rangeTo)
                .stream()
                .map(FunnelAnalyticsResponse::from)
                .toList();
    }

    @Operation(
            summary = "Peak shopping hours",
            description = "Order count and revenue by hour-of-day (0-23), from spark_batch_hourly_pattern " +
                    "(already computed by BatchAnalytics.hourOfDayPattern()/SparkBatchAnalyticsJob on every " +
                    "batch run - this endpoint only adds a read path, the table was already being written, " +
                    "just never read back by the API). meta.status is UNAVAILABLE if the Spark batch job has " +
                    "never run yet, EMPTY if it ran but found no orders, OK otherwise - same three-state " +
                    "convention as every other Spark-backed section of /analytics/unified."
    )
    @GetMapping("/peak-hours")
    public SparkSection getPeakShoppingHours() {
        return SparkSection.of(DataSource.SPARK_BATCH, sparkAnalyticsJdbcRepository.peakShoppingHours());
    }

    @Operation(
            summary = "Average order value",
            description = "One global AOV figure (totalRevenue / paidOrderCount) for an arbitrary [from, to) " +
                    "range, paid orders only. GET /analytics/time already returns orders/paidOrders/revenue " +
                    "per bucket if a trend over time is what's needed - this endpoint exists for the simpler, " +
                    "more common case of just wanting the current AOV number without fetching and dividing " +
                    "bucket rows client-side. Defaults to the last 30 days."
    )
    @GetMapping("/order-value")
    public OrderValueSummaryResponse getOrderValueSummary(
            @Parameter(description = "ISO-8601 instant, inclusive. Defaults to 30 days ago.")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "ISO-8601 instant, exclusive. Defaults to now.")
            @RequestParam(required = false) Instant to
    ) {
        Instant rangeTo = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.minus(30, ChronoUnit.DAYS);

        long paidOrderCount = orderAnalyticsRepository.countPaidBetween(rangeFrom, rangeTo);
        BigDecimal totalRevenue = orderAnalyticsRepository.sumPaidRevenueBetween(rangeFrom, rangeTo);

        return OrderValueSummaryResponse.of(rangeFrom, rangeTo, paidOrderCount, totalRevenue);
    }
}
