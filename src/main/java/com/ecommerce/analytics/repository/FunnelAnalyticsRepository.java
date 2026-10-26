package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.FunnelAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FunnelAnalyticsRepository extends JpaRepository<FunnelAnalytics, Instant> {

    Optional<FunnelAnalytics> findByBucketDate(Instant bucketDate);

    /**
     * PHASE 1, item 5 (conversion funnel metrics): FunnelAnalytics/
     * FunnelAnalyticsService already existed and were already being
     * populated correctly by the Kafka consumer pipeline (recordProductViewed/
     * recordCartItemAdded/recordCheckoutStarted/recordOrderCreated/
     * recordOrderPaid) -- the only gap was that nothing ever read this table
     * back out over a range. Added for the new GET /analytics/funnel
     * endpoint (AdvancedAnalyticsController).
     */
    List<FunnelAnalytics> findByBucketDateBetweenOrderByBucketDateDesc(Instant from, Instant to);
}
