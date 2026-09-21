package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.CategoryAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryAnalyticsRepository extends JpaRepository<CategoryAnalytics, Long> {
}
