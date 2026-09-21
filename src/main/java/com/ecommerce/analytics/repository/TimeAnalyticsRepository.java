package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.TimeAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeAnalyticsRepository extends JpaRepository<TimeAnalytics, Long> {

    Optional<TimeAnalytics> findByBucketTypeAndBucketStart(String bucketType, Instant bucketStart);

    List<TimeAnalytics> findByBucketTypeOrderByBucketStartDesc(String bucketType);
}
