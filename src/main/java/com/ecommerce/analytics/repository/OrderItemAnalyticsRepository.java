package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.OrderItemAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemAnalyticsRepository extends JpaRepository<OrderItemAnalytics, Long> {

    List<OrderItemAnalytics> findByOrderId(Long orderId);
}
