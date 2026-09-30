package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.analytics.unified.DataSource;
import com.ecommerce.analytics.dto.analytics.unified.SectionStatus;
import com.ecommerce.analytics.dto.analytics.unified.UnifiedAnalyticsResponse;
import com.ecommerce.analytics.repository.CategoryAnalyticsRepository;
import com.ecommerce.analytics.repository.CustomerAnalyticsRepository;
import com.ecommerce.analytics.repository.SparkAnalyticsJdbcRepository;
import com.ecommerce.analytics.repository.SparkQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnifiedAnalyticsServiceTest {

    private SparkAnalyticsJdbcRepository sparkRepository;
    private CustomerAnalyticsRepository customerAnalyticsRepository;
    private CategoryAnalyticsRepository categoryAnalyticsRepository;
    private UnifiedAnalyticsService service;

    @BeforeEach
    void setUp() {
        sparkRepository = mock(SparkAnalyticsJdbcRepository.class);
        customerAnalyticsRepository = mock(CustomerAnalyticsRepository.class);
        categoryAnalyticsRepository = mock(CategoryAnalyticsRepository.class);
        service = new UnifiedAnalyticsService(sparkRepository, customerAnalyticsRepository, categoryAnalyticsRepository);
    }

    @Test
    void get_shouldComposeAllSectionsWhenEverySourceIsAvailable() {
        when(customerAnalyticsRepository.count()).thenReturn(500L);
        when(customerAnalyticsRepository.countByPaidOrdersGreaterThan(0)).thenReturn(320L);
        when(categoryAnalyticsRepository.count()).thenReturn(15L);

        when(sparkRepository.revenueTrend(30)).thenReturn(
                SparkQueryResult.available(List.of(Map.of("day", "2026-09-28", "revenue", 999.0))));
        when(sparkRepository.topProductPerformance(10)).thenReturn(
                SparkQueryResult.available(List.of(Map.of("product_name", "Phone", "revenue", 500.0))));
        when(sparkRepository.categoryPerformance()).thenReturn(
                SparkQueryResult.available(List.of(Map.of("category_name", "Phones", "revenue", 500.0))));
        when(sparkRepository.topGeoPerformance(10)).thenReturn(
                SparkQueryResult.available(List.of(Map.of("city", "Kathmandu", "revenue", 200.0))));

        Timestamp windowEnd = Timestamp.valueOf("2026-09-29 10:01:00");
        when(sparkRepository.recentEventCounts(10)).thenReturn(
                SparkQueryResult.available(List.of(Map.of("event_type", "ORDER_CREATED", "window_end", windowEnd))));
        when(sparkRepository.recentOrderActivity(10)).thenReturn(
                SparkQueryResult.available(List.of(Map.of("orders_created", 3L, "window_end", windowEnd))));
        when(sparkRepository.recentPaidRevenue(10)).thenReturn(
                SparkQueryResult.available(List.of(Map.of("paid_count", 2L, "window_end", windowEnd))));

        UnifiedAnalyticsResponse response = service.get(30, 10, 10);

        assertNotNull(response.generatedAt());

        assertEquals(SectionStatus.OK, response.customerSummary().meta().status());
        assertEquals(DataSource.SPRING, response.customerSummary().meta().source());
        assertEquals(500L, response.customerSummary().totalCustomers());
        assertEquals(320L, response.customerSummary().customersWithPaidOrders());
        assertEquals(15L, response.customerSummary().totalCategories());

        assertEquals(SectionStatus.OK, response.revenueTrend().meta().status());
        assertEquals(DataSource.SPARK_BATCH, response.revenueTrend().meta().source());
        assertEquals(1, response.revenueTrend().rows().size());

        assertEquals(SectionStatus.OK, response.liveEventCounts().meta().status());
        assertEquals(DataSource.SPARK_STREAMING, response.liveEventCounts().meta().source());
        assertEquals(windowEnd.toInstant(), response.liveEventCounts().meta().asOf());
    }

    @Test
    void get_shouldReportSparkSectionsUnavailable_whenSparkTablesDoNotExist_withoutFallingBackToSpring() {
        when(customerAnalyticsRepository.count()).thenReturn(500L);
        when(customerAnalyticsRepository.countByPaidOrdersGreaterThan(0)).thenReturn(320L);
        when(categoryAnalyticsRepository.count()).thenReturn(15L);

        when(sparkRepository.revenueTrend(30)).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.topProductPerformance(10)).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.categoryPerformance()).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.topGeoPerformance(10)).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.recentEventCounts(10)).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.recentOrderActivity(10)).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.recentPaidRevenue(10)).thenReturn(SparkQueryResult.tableMissing());

        UnifiedAnalyticsResponse response = service.get(30, 10, 10);

        // Spark sections are honestly UNAVAILABLE...
        assertEquals(SectionStatus.UNAVAILABLE, response.revenueTrend().meta().status());
        assertEquals(SectionStatus.UNAVAILABLE, response.productPerformance().meta().status());
        assertEquals(SectionStatus.UNAVAILABLE, response.liveEventCounts().meta().status());
        assertEquals(0, response.revenueTrend().rows().size());

        // ...and Spring's own section is completely unaffected by Spark being unavailable.
        assertEquals(SectionStatus.OK, response.customerSummary().meta().status());
        assertEquals(500L, response.customerSummary().totalCustomers());
    }

    @Test
    void get_shouldReportEmptyDistinctlyFromUnavailable() {
        when(customerAnalyticsRepository.count()).thenReturn(0L);
        when(customerAnalyticsRepository.countByPaidOrdersGreaterThan(0)).thenReturn(0L);
        when(categoryAnalyticsRepository.count()).thenReturn(0L);

        when(sparkRepository.revenueTrend(30)).thenReturn(SparkQueryResult.empty());
        when(sparkRepository.topProductPerformance(10)).thenReturn(SparkQueryResult.tableMissing());
        when(sparkRepository.categoryPerformance()).thenReturn(SparkQueryResult.empty());
        when(sparkRepository.topGeoPerformance(10)).thenReturn(SparkQueryResult.empty());
        when(sparkRepository.recentEventCounts(10)).thenReturn(SparkQueryResult.empty());
        when(sparkRepository.recentOrderActivity(10)).thenReturn(SparkQueryResult.empty());
        when(sparkRepository.recentPaidRevenue(10)).thenReturn(SparkQueryResult.empty());

        UnifiedAnalyticsResponse response = service.get(30, 10, 10);

        assertEquals(SectionStatus.EMPTY, response.revenueTrend().meta().status());
        assertEquals(SectionStatus.UNAVAILABLE, response.productPerformance().meta().status());
        assertNull(response.revenueTrend().meta().asOf());
    }
}
