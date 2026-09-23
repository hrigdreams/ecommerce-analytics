package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.FunnelAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface FunnelAnalyticsRepository extends JpaRepository<FunnelAnalytics, Instant> {

    Optional<FunnelAnalytics> findByBucketDate(Instant bucketDate);
}
