package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.ReviewAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewAnalyticsRepository extends JpaRepository<ReviewAnalytics, Long> {

    List<ReviewAnalytics> findByProductId(Long productId);
}
