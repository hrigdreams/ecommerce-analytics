package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.OrderAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAnalyticsRepository extends JpaRepository<OrderAnalytics, Long> {
}
