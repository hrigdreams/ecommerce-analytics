package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.FunnelAnalytics;
import com.ecommerce.analytics.repository.FunnelAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunnelAnalyticsServiceTest {

    private FunnelAnalyticsRepository repository;
    private FunnelAnalyticsService service;

    // 2026-09-23 14:37:05 UTC -> day bucket 2026-09-23T00:00:00Z
    private final Instant at = Instant.parse("2026-09-23T14:37:05Z");
    private final Instant dayBucket = Instant.parse("2026-09-23T00:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(FunnelAnalyticsRepository.class);
        service = new FunnelAnalyticsService(repository);
        when(repository.findByBucketDate(any())).thenReturn(Optional.empty());
    }

    @Test
    void recordProductViewed_shouldCreateRowInCorrectDayBucket() {
        service.recordProductViewed(at);

        verify(repository).findByBucketDate(eq(dayBucket));
        verify(repository).save(argThat(r -> r.getProductViews() == 1 && r.getBucketDate().equals(dayBucket)));
    }

    @Test
    void recordCartItemAdded_shouldIncrementExistingRow() {
        FunnelAnalytics row = new FunnelAnalytics();
        row.setBucketDate(dayBucket);
        row.setCartItemAdded(4);
        when(repository.findByBucketDate(dayBucket)).thenReturn(Optional.of(row));

        service.recordCartItemAdded(at);

        assertEquals(5, row.getCartItemAdded());
    }

    @Test
    void recordCheckoutStarted_shouldIncrement() {
        FunnelAnalytics row = new FunnelAnalytics();
        row.setBucketDate(dayBucket);
        row.setCheckoutStarted(2);
        when(repository.findByBucketDate(dayBucket)).thenReturn(Optional.of(row));

        service.recordCheckoutStarted(at);

        assertEquals(3, row.getCheckoutStarted());
    }

    @Test
    void recordOrderCreated_shouldIncrement() {
        FunnelAnalytics row = new FunnelAnalytics();
        row.setBucketDate(dayBucket);
        row.setOrdersCreated(1);
        when(repository.findByBucketDate(dayBucket)).thenReturn(Optional.of(row));

        service.recordOrderCreated(at);

        assertEquals(2, row.getOrdersCreated());
    }

    @Test
    void recordOrderPaid_shouldIncrement() {
        FunnelAnalytics row = new FunnelAnalytics();
        row.setBucketDate(dayBucket);
        row.setOrdersPaid(0);
        when(repository.findByBucketDate(dayBucket)).thenReturn(Optional.of(row));

        service.recordOrderPaid(at);

        assertEquals(1, row.getOrdersPaid());
    }

    @Test
    void allStagesOnSameDay_shouldAccumulateOnOneRow() {
        FunnelAnalytics row = new FunnelAnalytics();
        row.setBucketDate(dayBucket);
        when(repository.findByBucketDate(dayBucket)).thenReturn(Optional.of(row));

        service.recordProductViewed(at);
        service.recordCartItemAdded(at);
        service.recordCheckoutStarted(at);
        service.recordOrderCreated(at);
        service.recordOrderPaid(at);

        assertEquals(1, row.getProductViews());
        assertEquals(1, row.getCartItemAdded());
        assertEquals(1, row.getCheckoutStarted());
        assertEquals(1, row.getOrdersCreated());
        assertEquals(1, row.getOrdersPaid());
    }

    private static FunnelAnalytics argThat(java.util.function.Predicate<FunnelAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }
}
