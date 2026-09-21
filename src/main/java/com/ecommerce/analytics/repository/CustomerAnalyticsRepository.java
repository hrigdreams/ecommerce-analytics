package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.CustomerAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface CustomerAnalyticsRepository extends JpaRepository<CustomerAnalytics, Long> {

    long countByFirstOrderAtBetween(Instant from, Instant to);

    long countByPaidOrdersGreaterThan(long paidOrders);
}
