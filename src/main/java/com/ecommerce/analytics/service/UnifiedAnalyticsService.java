package com.ecommerce.analytics.service;

import com.ecommerce.analytics.config.CacheConfig;
import com.ecommerce.analytics.dto.analytics.unified.CustomerSummarySection;
import com.ecommerce.analytics.dto.analytics.unified.DataSource;
import com.ecommerce.analytics.dto.analytics.unified.SectionMeta;
import com.ecommerce.analytics.dto.analytics.unified.SparkSection;
import com.ecommerce.analytics.dto.analytics.unified.UnifiedAnalyticsResponse;
import com.ecommerce.analytics.repository.CategoryAnalyticsRepository;
import com.ecommerce.analytics.repository.CustomerAnalyticsRepository;
import com.ecommerce.analytics.repository.SparkAnalyticsJdbcRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;

// not touching this
@Service
public class UnifiedAnalyticsService {

    private final SparkAnalyticsJdbcRepository sparkRepository;
    private final CustomerAnalyticsRepository customerAnalyticsRepository;
    private final CategoryAnalyticsRepository categoryAnalyticsRepository;

    public UnifiedAnalyticsService(
            SparkAnalyticsJdbcRepository sparkRepository,
            CustomerAnalyticsRepository customerAnalyticsRepository,
            CategoryAnalyticsRepository categoryAnalyticsRepository
    ) {
        this.sparkRepository = sparkRepository;
        this.customerAnalyticsRepository = customerAnalyticsRepository;
        this.categoryAnalyticsRepository = categoryAnalyticsRepository;
    }

// quick note
    @Cacheable(cacheNames = CacheConfig.UNIFIED_ANALYTICS_CACHE, key = "#trendDays + '_' + #topN + '_' + #liveWindows")
    public UnifiedAnalyticsResponse get(int trendDays, int topN, int liveWindows) {
        return new UnifiedAnalyticsResponse(
                Instant.now(),
                customerSummary(),
                SparkSection.of(DataSource.SPARK_BATCH, sparkRepository.revenueTrend(trendDays)),
                SparkSection.of(DataSource.SPARK_BATCH, sparkRepository.topProductPerformance(topN)),
                SparkSection.of(DataSource.SPARK_BATCH, sparkRepository.categoryPerformance()),
                SparkSection.of(DataSource.SPARK_BATCH, sparkRepository.topGeoPerformance(topN)),
                SparkSection.of(DataSource.SPARK_STREAMING, sparkRepository.recentEventCounts(liveWindows)),
                SparkSection.of(DataSource.SPARK_STREAMING, sparkRepository.recentOrderActivity(liveWindows)),
                SparkSection.of(DataSource.SPARK_STREAMING, sparkRepository.recentPaidRevenue(liveWindows))
        );
    }

    private CustomerSummarySection customerSummary() {
        long totalCustomers = customerAnalyticsRepository.count();
        long customersWithPaidOrders = customerAnalyticsRepository.countByPaidOrdersGreaterThan(0);
        long totalCategories = categoryAnalyticsRepository.count();

        return new CustomerSummarySection(
                SectionMeta.ok(DataSource.SPRING, Instant.now()),
                totalCustomers,
                customersWithPaidOrders,
                totalCategories
        );
    }
}
