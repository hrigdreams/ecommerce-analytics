package com.ecommerce.analytics.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caches for the heavy aggregate analytics endpoints (customer summary, time
 * rollups). These scan/aggregate many rows, and the underlying data only
 * changes as Kafka events are consumed, so a short TTL trades a few seconds
 * of staleness for a lot fewer DB hits on a dashboard that gets polled.
 *
 * Not used for single-row lookups (GET /analytics/products/{id} etc.) —
 * those are already cheap primary-key reads and staleness there is less
 * acceptable (e.g. right after creating a product).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CUSTOMER_SUMMARY_CACHE = "customerSummary";
    public static final String TIME_ANALYTICS_CACHE = "timeAnalytics";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CUSTOMER_SUMMARY_CACHE, TIME_ANALYTICS_CACHE
        );
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(500)
        );
        return manager;
    }
}
