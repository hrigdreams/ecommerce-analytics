package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.ProductAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAnalyticsRepository extends JpaRepository<ProductAnalytics, Long> {
}
