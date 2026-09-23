package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.TimeAnalytics;
import com.ecommerce.analytics.repository.TimeAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeAnalyticsServiceTest {

    private TimeAnalyticsRepository repository;
    private TimeAnalyticsService service;

    // Wednesday, 2026-09-23 14:37:05 UTC
    private final Instant at = Instant.parse("2026-09-23T14:37:05Z");

    @BeforeEach
    void setUp() {
        repository = mock(TimeAnalyticsRepository.class);
        service = new TimeAnalyticsService(repository);
        when(repository.findByBucketTypeAndBucketStart(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void recordOrderCreated_shouldWriteAllFiveBuckets() {
        service.recordOrderCreated(at);

        // HOUR, DAY, WEEK, MONTH, YEAR = 5 rollups
        verify(repository, times(5)).save(any());
    }

    @Test
    void recordOrderCreated_shouldTruncateHourBucketCorrectly() {
        service.recordOrderCreated(at);

        verify(repository).findByBucketTypeAndBucketStart(
                eq(TimeAnalytics.HOUR), eq(Instant.parse("2026-09-23T14:00:00Z")));
    }

    @Test
    void recordOrderCreated_shouldAlignWeekBucketToMonday() {
        service.recordOrderCreated(at);

        // 2026-09-23 is a Wednesday; the Monday of that week is 2026-09-21.
        verify(repository).findByBucketTypeAndBucketStart(
                eq(TimeAnalytics.WEEK), eq(Instant.parse("2026-09-21T00:00:00Z")));
    }

    @Test
    void recordOrderCreated_shouldAlignMonthBucketToTheFirst() {
        service.recordOrderCreated(at);

        verify(repository).findByBucketTypeAndBucketStart(
                eq(TimeAnalytics.MONTH), eq(Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void recordOrderPaid_shouldAccumulateRevenueAndUnitsOnExistingBucket() {
        TimeAnalytics dayRow = new TimeAnalytics();
        dayRow.setBucketType(TimeAnalytics.DAY);
        dayRow.setBucketStart(Instant.parse("2026-09-23T00:00:00Z"));
        dayRow.setPaidOrders(1);
        dayRow.setRevenue(new BigDecimal("100.00"));
        dayRow.setUnitsSold(2);

        when(repository.findByBucketTypeAndBucketStart(eq(TimeAnalytics.DAY), any()))
                .thenReturn(Optional.of(dayRow));

        service.recordOrderPaid(at, new BigDecimal("49.99"), 3);

        assertEquals(2, dayRow.getPaidOrders());
        assertEquals(new BigDecimal("149.99"), dayRow.getRevenue());
        assertEquals(5, dayRow.getUnitsSold());
    }

    @Test
    void recordOrderCreated_shouldUseNowWhenTimestampMissing() {
        service.recordOrderCreated(null);

        verify(repository, times(5)).save(any());
    }
}
