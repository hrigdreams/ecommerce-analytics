package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.GeoAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeoAnalyticsRepository extends JpaRepository<GeoAnalytics, Long> {

    Optional<GeoAnalytics> findByCountryAndCity(String country, String city);
}
